(function install(window) {
    'use strict';
    if (window.__ngEpubContent) return;
    var document = window.document, Node = window.Node;
    window.__ngEpubContentInstall = function (target) {
        if (target.location.origin !== window.location.origin) throw new Error('EPUB document origin mismatch');
        if (window.__ngEpubLinesInstall) window.__ngEpubLinesInstall(target);
        install(target);
    };
    var attribute = 'data-ng-epub-source', key = null, entries = [], media = [], generated = [];
    var nodeEntries = new WeakMap(), original = new Map(), hidden = new Map();
    var whitespace = new Map();
    var styledNodes = [], styleElements = [], fontFaces = [], currentStyles = [], fontPromise = null;
    var baseEntries = [], contentLength = 0, styleRevision = 0, styleSignature = '', geometrySignature = '';
    var fontCache = new Map();
    var lastStylePayload = null, aloud = null, nightMode = false;
    var noteMarkers = [], noteElements = [], noteStyle = null, noteSignature = '[[],null]', noteImage = null;
    var wordWiseFragments = [], wordWiseStyle = null, wordWiseSignature = '[[],null]', wordWiseLayer = null;
    var wordWiseRectsCache = null, wordWiseMeasure = null;
    var titleSegments = [], titleStyle = null, titleSignature = '[[],null]';
    function palette(value) { return nightMode && value.nightCharStyles ? value.nightCharStyles : value.charStyles || []; }
    function paintRanges(value) { return nightMode && value.nightStyleRanges ? value.nightStyleRanges : value.styleRanges || []; }
    function signature(value) { return JSON.stringify([palette(value), paintRanges(value)]); }
    function geometry(value) {
        // Fonts/images are shared by both palettes; color-only paint splits never reflow text.
        var spans = [];
        (value.styleRanges || []).forEach(function (r) {
            var style = (value.charStyles || [])[r[2]] || {};
            var font = JSON.stringify([style.font || null, style.weight == null ? null : style.weight, style.italic == null ? null : style.italic, style.nineSlice || null]);
            if (font === '[null,null,null,null]') return;
            var last = spans[spans.length - 1];
            if (last && last[1] === r[0] && last[2] === font) last[1] = r[1];
            else spans.push([r[0], r[1], font]);
        });
        return JSON.stringify(spans);
    }
    function resetStyles() {
        clearStyleImages();
        clearUnderlines();
        styledNodes.forEach(function (item) { item.wrapper.replaceWith(item.node); });
        styledNodes = []; styleElements = []; noteElements = []; fontPromise = null;
        entries = baseEntries.slice(); nodeEntries = new WeakMap();
        entries.forEach(function (entry) { nodeEntries.set(entry.node, entry); });
    }
    function updateStyles(value) {
        if (value.key !== key) throw new Error('字样式正文已变更');
        if (signature(value) === styleSignature) {
            lastStylePayload = Object.assign({}, value, { text: { length: contentLength } });
            return { changed: false, reflow: false };
        }
        var reflow = geometry(value) !== geometrySignature;
        resetStyles();
        applyStyles(Object.assign({}, value, { text: { length: contentLength } }));
        return { changed: true, reflow: reflow };
    }
    var underlineFragments = [], underlineLayer;
    function setTitleSegments(values, style) {
        var signature = JSON.stringify([values || [], style || null]);
        if (signature === titleSignature) return false;
        titleSegments = (values || []).slice().sort(function (a,b) { return a.from-b.from; });
        titleStyle = style; titleSignature = signature;
        if (lastStylePayload) { resetStyles(); applyStyles(lastStylePayload); }
        return true;
    }
    function titleHeadings() {
        return styleElements.filter(function (item) { return currentStyles[item.style].titleSegment != null; })
            .map(function (item) { return item.node.closest('h1,h2,h3,h4,h5,h6,[role="heading"]'); }).filter(Boolean);
    }
    function setNotes(values, style) {
        var signature = JSON.stringify([values || [], style || null]);
        if (signature === noteSignature) return false;
        if (values && values.length && (!style || !['size','gap','trailing','touch'].every(function (name) {
            return Number.isFinite(style[name]) && style[name] >= 0 && style[name] <= 256;
        }) || !/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(style.image))) throw new Error('批注标记参数无效');
        noteMarkers = (values || []).slice(); noteStyle = style; noteSignature = signature;
        if (noteStyle && (!noteImage || noteImage.url !== noteStyle.image)) {
            var image = new window.Image();
            noteImage = { url: noteStyle.image, promise: new Promise(function (resolve) {
                image.onload = function () { resolve([]); }; image.onerror = function () { resolve(['批注标记图片无法加载']); };
                image.src = noteStyle.image;
            }) };
        }
        if (lastStylePayload) { resetStyles(); applyStyles(lastStylePayload); }
        return true;
    }
    function noteRects() {
        if (!noteStyle) return [];
        var occupiedSvg = new Map();
        return noteElements.map(function (item) {
            var r = item.node.getBoundingClientRect(), css = window.getComputedStyle(item.node);
            var vertical = /^(vertical|sideways)/.test(css.writingMode);
            var scale = bodyTransform().scale, size = noteStyle.size * scale, gap = noteStyle.gap * scale;
            var left = vertical ? r.left : r.left + gap;
            var top = vertical ? r.top + gap : r.top;
            if (item.svg) {
                var svg = item.node.ownerSVGElement, occupied = occupiedSvg.get(svg);
                if (!occupied) {
                    occupied = []; var range = document.createRange();
                    entries.forEach(function (entry) {
                        if (!svg.contains(entry.node) || !entry.node.parentElement.closest('text')) return;
                        range.selectNodeContents(entry.node);
                        Array.from(range.getClientRects()).forEach(function (box) { if (box.width && box.height) occupied.push(box); });
                    }); occupiedSvg.set(svg, occupied);
                }
                left = vertical ? r.left : css.direction === 'rtl' ? r.left-gap-size : r.right+gap;
                top = vertical ? r.bottom+gap : r.bottom-size;
                var viewport = document.documentElement, width = viewport.clientWidth, height = viewport.clientHeight;
                if (r.right > 0 && r.left < width && r.bottom > 0 && r.top < height) {
                    // SVG labels have explicit coordinates. Place the existing marker in
                    // the nearest free area instead of inserting characters or moving art.
                    var candidates = [[left,top]], step = size+gap;
                    for (var ring=1;ring<=12;ring++) [[0,-1],[0,1],[-1,0],[1,0],[-1,-1],[1,-1],[-1,1],[1,1]].forEach(function (d) {
                        candidates.push([left+d[0]*step*ring,top+d[1]*step*ring]);
                    });
                    var found = candidates.find(function (p) {
                        p[0]=Math.max(0,Math.min(width-size,p[0]));p[1]=Math.max(0,Math.min(height-size,p[1]));
                        return !occupied.some(function (box) { return Math.min(p[0]+size,box.right)-Math.max(p[0],box.left)>.5 &&
                            Math.min(p[1]+size,box.bottom)-Math.max(p[1],box.top)>.5; });
                    });
                    if (found) { left=found[0];top=found[1]; }
                }
                occupied.push({left:left,top:top,right:left+size,bottom:top+size});
            }
            return { id: item.id, order: item.order, image: noteStyle.image, left: left, top: top, right: left + size, bottom: top + size,
                width: size, height: size, touch: noteStyle.touch * scale };
        });
    }
    function wordWiseWritingSupported() {
        if (!document.body) return false;
        var css = window.getComputedStyle(document.body);
        var writing = css.writingMode || css.webkitWritingMode || '';
        // Word Gloss geometry assumes horizontal LTR boxes. Vertical and RTL
        // documents are out of scope; suppress the overlay rather than paint
        // labels against the wrong axis.
        return !/^(vertical|sideways)/.test(writing) && css.direction !== 'rtl';
    }
    function setWordWise(values, style) {
        // A vertical/RTL document must not reserve a first-line band or leave
        // untappable fragments behind; treat it as an empty Word Gloss layer.
        if (!wordWiseWritingSupported()) { values = []; style = null; }
        var signature = JSON.stringify([values || [], style || null]);
        if (signature === wordWiseSignature) return false;
        wordWiseFragments = (values || []).slice(); wordWiseStyle = style; wordWiseSignature = signature;
        wordWiseRectsCache = null;
        paintWordWise();
        return true;
    }
    function computeWordWiseRects() {
        if (!wordWiseFragments.length) return [];
        var result = [];
        wordWiseFragments.forEach(function (fragment) {
            // Native positions are unique across the chapter text, so iterate the
            // runs map directly instead of matching projected element/ordinal ids,
            // which change after line fragmentation.
            entries.forEach(function (entry) {
                var range = rangeForEntry(entry, fragment.start, fragment.end);
                if (!range) return;
                Array.from(range.getClientRects()).forEach(function (rect) {
                    if (rect.width <= 0 || rect.height <= 0) return;
                    result.push({ id: fragment.id, hint: fragment.hint, left: rect.left, top: rect.top,
                        right: rect.right, bottom: rect.bottom });
                });
            });
        });
        return result;
    }
    function wordWiseRects() {
        if (!wordWiseRectsCache) wordWiseRectsCache = computeWordWiseRects();
        return wordWiseRectsCache;
    }
    function rangeForEntry(entry, nativeStart, nativeEnd) {
        var node = entry.node, runs = entry.runs;
        var charStart = null, charEnd = null;
        for (var i = 0; i < runs.length; i++) {
            var r = runs[i];
            var from = r[0], to = r[1], ns = r[2], ne = r[3], linear = r[4];
            if (ne <= nativeStart || ns >= nativeEnd) continue;
            var runFrom = linear ? from + Math.max(0, nativeStart - ns) : from;
            var runTo = linear ? from + Math.min(to - from, nativeEnd - ns) : to;
            charStart = charStart == null ? runFrom : Math.min(charStart, runFrom);
            charEnd = charEnd == null ? runTo : Math.max(charEnd, runTo);
        }
        if (charStart == null || charEnd <= charStart) return null;
        var range = document.createRange();
        range.setStart(node, charStart);
        range.setEnd(node, charEnd);
        return range;
    }
    var wordWiseRafPending = false;
    var LABEL_GAP = 6, LABEL_MIN_WIDTH = 4, LABEL_ABOVE = 4, LABEL_COLUMN_INSET = 8;
    function sameLine(a, b) {
        var overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        var shorter = Math.min(a.bottom - a.top, b.bottom - b.top);
        return shorter > 0 && overlap > shorter / 2;
    }
    function onPage(rect, width, height) {
        var cx = (rect.left + rect.right) / 2, cy = (rect.top + rect.bottom) / 2;
        return cx >= 0 && cx < width && cy >= 0 && cy < height;
    }
    function mergeLineSegments(rects) {
        var lines = [];
        rects.forEach(function (rect) {
            var group = null;
            for (var i = 0; i < lines.length; i++) {
                if (sameLine(lines[i], rect)) { group = lines[i]; break; }
            }
            if (!group) { lines.push({ id: rect.id, hint: rect.hint, left: rect.left, right: rect.right,
                top: rect.top, bottom: rect.bottom }); return; }
            group.left = Math.min(group.left, rect.left);
            group.right = Math.max(group.right, rect.right);
            group.top = Math.min(group.top, rect.top);
            group.bottom = Math.max(group.bottom, rect.bottom);
        });
        return lines;
    }
    function placeHintX(spanStart, spanEnd, measuredWidth, lineStart, lineEnd, cursor) {
        var colLeft = Math.max(lineStart, cursor), colRight = lineEnd;
        if (colRight - colLeft < LABEL_MIN_WIDTH || colLeft >= spanEnd) return { x: colLeft, maxWidth: 0 };
        var x = Math.min(spanStart, colRight - measuredWidth);
        x = Math.max(x, colLeft);
        x = Math.min(x, spanEnd - 1);
        x = Math.max(x, colLeft);
        return { x: x, maxWidth: Math.max(0, colRight - x) };
    }
    function chooseAnchor(segments, measuredWidth, columnLeft, columnRight) {
        if (!segments.length) return null;
        var ordered = segments.slice().sort(function (a, b) { return a.top - b.top || a.left - b.left; });
        function fits(segment) {
            return placeHintX(segment.left, segment.right, measuredWidth, columnLeft, columnRight, columnLeft).maxWidth >= measuredWidth - 0.5;
        }
        function visibleWidth(segment) {
            return Math.max(0, Math.min(segment.right, columnRight) - Math.max(segment.left, columnLeft));
        }
        var full = ordered.find(fits);
        if (full) return full;
        return ordered.reduce(function (best, segment) {
            var a = visibleWidth(segment), b = visibleWidth(best);
            if (a !== b) return a > b ? segment : best;
            return segment.top < best.top || (segment.top === best.top && segment.left < best.left) ? segment : best;
        });
    }
    function packHintLabels(anchors, columnLeft, columnRight) {
        var groups = [];
        anchors.slice().sort(function (a, b) { return a.top - b.top || a.left - b.left; }).forEach(function (anchor) {
            var group = null;
            for (var i = 0; i < groups.length; i++) {
                if (sameLine(groups[i][0], anchor)) { group = groups[i]; break; }
            }
            if (!group) groups.push([anchor]); else group.push(anchor);
        });
        var placed = [];
        groups.forEach(function (group) {
            var slots = [];
            group.slice().sort(function (a, b) { return a.left - b.left; }).forEach(function (segment) {
                var measured = segment.measured;
                // Match placeHintX: slide left until the full label fits, even
                // if that takes it left of the word's start. An empty prefix of
                // the line is better than an unnecessary ellipsis.
                var preferred = Math.max(columnLeft, Math.min(segment.left, columnRight - measured));
                var cursor = slots.length ? slots[slots.length - 1].x + slots[slots.length - 1].width + LABEL_GAP : columnLeft;
                if (preferred < cursor && slots.length) {
                    var need = cursor - preferred, j;
                    for (j = slots.length - 1; j >= 0 && need > 0; j--) {
                        var can = slots[j].x - columnLeft;
                        if (can <= 0) continue;
                        var take = Math.min(can, need), k;
                        for (k = j; k < slots.length; k++) slots[k].x -= take;
                        need -= take;
                    }
                    cursor = slots[slots.length - 1].x + slots[slots.length - 1].width + LABEL_GAP;
                }
                var x = Math.max(preferred, cursor);
                x = Math.min(x, segment.right - 1);
                x = Math.max(x, cursor);
                var width = Math.min(measured, Math.max(0, columnRight - x));
                if (width < LABEL_MIN_WIDTH) return;
                slots.push({ id: segment.id, hint: segment.hint, left: segment.left, right: segment.right,
                    top: segment.top, bottom: segment.bottom, x: x, width: width });
            });
            slots.forEach(function (slot) {
                placed.push({ id: slot.id, hint: slot.hint, left: slot.x, maxWidth: slot.width,
                    segment: { id: slot.id, hint: slot.hint, left: slot.left, right: slot.right,
                        top: slot.top, bottom: slot.bottom } });
            });
        });
        return placed;
    }
    function hintWidth(text, fontSize) {
        if (!wordWiseMeasure) {
            wordWiseMeasure = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
            wordWiseMeasure.setAttribute('aria-hidden', 'true');
            var measureStyle = { position: 'absolute', visibility: 'hidden', 'white-space': 'nowrap',
                margin: '0', padding: '0', border: '0', left: '0', top: '0', 'pointer-events': 'none' };
            Object.keys(measureStyle).forEach(function (name) { wordWiseMeasure.style.setProperty(name, measureStyle[name], 'important'); });
            document.documentElement.appendChild(wordWiseMeasure);
        }
        wordWiseMeasure.style.setProperty('font-size', fontSize + 'px', 'important');
        wordWiseMeasure.style.setProperty('line-height', (fontSize + 2) + 'px', 'important');
        wordWiseMeasure.textContent = text;
        // offsetWidth rounds down to an integer. CJK runs are often a
        // fraction of a pixel wider, and CSS text-overflow then replaces the
        // last glyph with an ellipsis even though the line has plenty of
        // room. Measure fractionally and keep a 1px safety margin.
        var measured = wordWiseMeasure.getBoundingClientRect().width;
        if (!(measured > 0)) measured = wordWiseMeasure.offsetWidth;
        return measured + 1;
    }
    function paintWordWise() {
        paintWordWiseCore();
        // The page move transform may settle one frame after ready; repaint once
        // so labels track the final viewport position instead of freezing.
        if (!wordWiseRafPending) {
            wordWiseRafPending = true;
            window.requestAnimationFrame(function () {
                wordWiseRafPending = false;
                paintWordWiseCore();
            });
        }
    }
    function paintWordWiseCore() {
        if (wordWiseLayer) { wordWiseLayer.replaceChildren(); wordWiseLayer.__ngHits = []; }
        if (!wordWiseFragments.length || !document.body) return;
        if (!wordWiseWritingSupported()) return;
        if (!wordWiseLayer) {
            wordWiseLayer = document.createElementNS('http://www.w3.org/1999/xhtml', 'ng-word-wise');
            wordWiseLayer.setAttribute('aria-hidden', 'true');
            var layerStyle = { position: 'fixed', inset: '0', 'pointer-events': 'none',
                'z-index': '2147483638', overflow: 'hidden', 'user-select': 'none' };
            Object.keys(layerStyle).forEach(function (name) { wordWiseLayer.style.setProperty(name, layerStyle[name], 'important'); });
            document.documentElement.appendChild(wordWiseLayer);
        }
        var color = wordWiseStyle && wordWiseStyle.color ? wordWiseStyle.color : '#00A8FF';
        var labelSize = wordWiseStyle && wordWiseStyle.fontSize ? wordWiseStyle.fontSize : 11;
        var byId = new Map();
        wordWiseRectsCache = computeWordWiseRects();
        wordWiseRectsCache.forEach(function (rect) {
            if (!byId.has(rect.id)) byId.set(rect.id, []);
            byId.get(rect.id).push(rect);
        });
        var viewport = document.documentElement, width = viewport.clientWidth, height = viewport.clientHeight;
        var columnLeft = 0, columnRight = Math.max(columnLeft + LABEL_MIN_WIDTH, width - LABEL_COLUMN_INSET);
        function visible(rect) { return rect.top < height && rect.bottom > 0 && rect.left < width && rect.right > 0; }
        byId.forEach(function (rects) {
            rects.forEach(function (rect) {
                if (!visible(rect)) return;
                var div = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
                var properties = { position: 'absolute', 'pointer-events': 'none', 'border-radius': '2px',
                    left: rect.left + 'px', top: rect.top + 'px',
                    width: (rect.right - rect.left) + 'px', height: (rect.bottom - rect.top) + 'px',
                    'background-color': color, opacity: '0.18' };
                Object.keys(properties).forEach(function (name) { div.style.setProperty(name, properties[name], 'important'); });
                wordWiseLayer.appendChild(div);
            });
        });
        // One label per hint id on this page. Wrapped fragments share that id;
        // extra line boxes keep the tint only.
        var anchors = [];
        byId.forEach(function (rects) {
            var pageRects = rects.filter(function (rect) { return onPage(rect, width, height); });
            if (!pageRects.length) return;
            var segments = mergeLineSegments(pageRects);
            var measured = hintWidth(pageRects[0].hint || '', labelSize);
            var anchor = chooseAnchor(segments, measured, columnLeft, columnRight);
            if (anchor && anchor.hint) {
                anchor.measured = measured;
                anchors.push(anchor);
            }
        });
        var hits = [];
        packHintLabels(anchors, columnLeft, columnRight).forEach(function (item) {
            var top = Math.max(0, item.segment.top - labelSize - LABEL_ABOVE);
            if (top + labelSize + 2 > height) top = Math.max(0, height - labelSize - 2);
            var label = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
            label.textContent = item.hint;
            var labelStyle = { position: 'absolute', 'pointer-events': 'none', margin: '0', padding: '0', border: '0',
                'font-size': labelSize + 'px', 'line-height': (labelSize + 2) + 'px', color: color,
                'white-space': 'nowrap', overflow: 'hidden', 'text-overflow': 'ellipsis',
                // Explicit width keeps shrink-to-fit from making the label
                // narrower than the width the packer actually reserved.
                width: item.maxWidth + 'px', 'max-width': item.maxWidth + 'px',
                left: item.left + 'px', top: top + 'px' };
            Object.keys(labelStyle).forEach(function (name) { label.style.setProperty(name, labelStyle[name], 'important'); });
            wordWiseLayer.appendChild(label);
            var drawn = Math.min(label.offsetWidth || item.maxWidth, item.maxWidth);
            hits.push({ id: item.id, hint: item.hint, left: item.left, top: top,
                right: item.left + drawn, bottom: top + labelSize + 2,
                segLeft: item.segment.left, segTop: item.segment.top,
                segRight: item.segment.right, segBottom: item.segment.bottom });
        });
        wordWiseLayer.__ngHits = hits;
    }
    function hitWordWise(x, y) {
        var hits = wordWiseLayer && wordWiseLayer.__ngHits || [];
        var i, hit = null;
        for (i = 0; i < hits.length; i++) {
            var label = hits[i];
            if (x >= label.left && x <= label.right && y >= label.top && y <= label.bottom) hit = label;
        }
        if (hit) return { id: hit.id, hint: hit.hint, left: hit.segLeft, top: hit.segTop,
            right: hit.segRight, bottom: hit.segBottom };
        var rects = wordWiseRects();
        for (i = 0; i < rects.length; i++) {
            var box = rects[i];
            if (x >= box.left && x <= box.right && y >= box.top && y <= box.bottom) hit = box;
        }
        return hit ? { id: hit.id, hint: hit.hint, left: hit.left, top: hit.top, right: hit.right, bottom: hit.bottom } : null;
    }
    function clearWordWise() {
        wordWiseRectsCache = null;
        if (wordWiseLayer) { wordWiseLayer.replaceChildren(); wordWiseLayer.__ngHits = []; }
    }
    function applyStyles(value) {
        lastStylePayload = value;
        styleRevision++;
        styleSignature = signature(value); geometrySignature = geometry(value);
        currentStyles = palette(value).map(function (style) {
            return style.underline > 0 ? Object.assign({}, style, { nativeUnderline:style.underline }) : style;
        });
        var ranges = paintRanges(value);
        if (!ranges.length && !aloud && !noteMarkers.length && !titleSegments.length) return;
        var titleBreaks = new Set();
        titleSegments.forEach(function (segment, index) {
            if (!titleStyle || !segment.breakBefore || !index) return;
            var previous = point(titleSegments[index - 1].to, true), start = point(segment.from, false);
            if (!previous || !start || previous.node.nodeType !== Node.TEXT_NODE || start.node.nodeType !== Node.TEXT_NODE) return;
            var gap = document.createRange(); gap.setStart(previous.node, previous.offset); gap.setEnd(start.node, start.offset);
            if (gap.cloneContents().querySelector('br') || /[\r\n]/.test(gap.toString()) &&
                /^(pre|pre-wrap|pre-line|break-spaces)$/.test(window.getComputedStyle(start.node.parentElement).whiteSpace)) return;
            titleBreaks.add(index);
        });
        var noteEnds = new Map();
        noteMarkers.forEach(function (note, order) {
            if (!Number.isInteger(note.end) || note.end <= 0 || note.end > contentLength) return;
            var at = point(note.end, true); if (!at || at.node.nodeType !== Node.TEXT_NODE) return;
            if (at.offset === 0) {
                var previous = entries[entries.findIndex(function (entry) { return entry.node === at.node; }) - 1];
                if (!previous) return;
                at = { node: previous.node, offset: previous.node.length };
            }
            var ends = noteEnds.get(at.node); if (!ends) { ends = new Map(); noteEnds.set(at.node, ends); }
            // Native notes arrive in time order; coincident endpoints draw/hit the newest marker.
            ends.set(at.offset, { id: note.id, order: order });
        });
        var aloudStyles = new Map(), titleStyles = new Map();
        var previous = 0;
        ranges.forEach(function (r) {
            if (!Array.isArray(r) || r.length !== 3 || !r.every(Number.isInteger) || r[0] < previous ||
                r[1] <= r[0] || r[1] > value.text.length || !currentStyles[r[2]]) throw new Error('字样式范围无效');
            previous = r[1];
        });
        function rangeAt(position) {
            if (position == null) return -1;
            var lo = 0, hi = ranges.length;
            while (lo < hi) { var mid = (lo + hi) >>> 1; if (ranges[mid][0] <= position) lo = mid + 1; else hi = mid; }
            return lo && position < ranges[lo - 1][1] ? lo - 1 : -1;
        }
        function styleAt(position) {
            var index = rangeAt(position);
            var id = index < 0 ? -1 : ranges[index][2];
            if (titleStyle && position != null) {
                var lo = 0, hi = titleSegments.length;
                while (lo < hi) { var mid = (lo + hi) >>> 1; if (titleSegments[mid].from <= position) lo = mid + 1; else hi = mid; }
                var titleIndex = lo - 1, segment = titleSegments[titleIndex];
                if (segment && position < segment.to) {
                    var key = id + '/' + titleIndex;
                    if (!titleStyles.has(key)) {
                        titleStyles.set(key, currentStyles.length);
                        currentStyles.push(Object.assign({}, currentStyles[id] || {}, { titleSegment:titleIndex,
                            size:titleStyle.fontSize * segment.scale,
                            lineHeight:segment.main === false ? titleStyle.subLineHeight || titleStyle.lineHeight * segment.scale : titleStyle.lineHeight }));
                    }
                    id = titleStyles.get(key);
                }
            }
            if (aloud && position != null && position >= aloud.from && position < aloud.to) {
                if (!aloudStyles.has(id)) {
                    var overlay = Object.assign({}, currentStyles[id] || {}, { color: aloud.color });
                    if (aloud.underline) Object.assign(overlay, { underline: 1, lineColor: aloud.color, lineWidth: 1, lineOffset: 1 });
                    aloudStyles.set(id, currentStyles.length); currentStyles.push(overlay);
                }
                return aloudStyles.get(id);
            }
            return id;
        }
        function sliceRuns(runs, from, to) {
            return runs.filter(function (r) { return r[0] < to && r[1] > from; }).map(function (r) {
                var a = Math.max(r[0], from), b = Math.min(r[1], to);
                return [a - from, b - from, r[4] ? r[2] + a - r[0] : r[2], r[4] ? r[2] + b - r[0] : r[3], r[4]];
            });
        }
        var expanded = [];
        var noteSegmenter = noteMarkers.length && window.Intl.Segmenter &&
            new window.Intl.Segmenter(undefined, { granularity: 'grapheme' });
        entries.forEach(function (entry) {
            var node = entry.node, parts = [], start = 0, id = -1, runIndex = 0;
            var svgText = node.parentElement && node.parentElement.namespaceURI === 'http://www.w3.org/2000/svg';
            // SVG text only renders SVG text-content elements. An HTML inline wrapper
            // makes its glyphs disappear; non-text SVG nodes must keep their structure.
            if (svgText && !node.parentElement.closest('text')) { expanded.push(entry); return; }
            for (var i = 0; i < node.length;) {
                while (runIndex < entry.runs.length && entry.runs[runIndex][1] <= i) runIndex++;
                var r = entry.runs[runIndex];
                var nextId = styleAt(r && r[0] <= i ? (r[4] ? r[2] + i - r[0] : r[2]) : null);
                if (nextId !== id) { if (i > start) parts.push([start, i, id]); start = i; id = nextId; }
                i += node.data.codePointAt(i) > 65535 ? 2 : 1;
            }
            if (start < node.length) parts.push([start, node.length, id]);
            var noteOffsets = noteEnds.get(node) || new Map();
            if (noteOffsets.size) {
                var divided = [], boundaries = Array.from(noteOffsets.keys()).sort(function (a,b) { return a-b; });
                // Keep only the final rendered grapheme with its empty marker box. This is
                // visual no-wrap grouping, never a word/selection/rule boundary calculation.
                var ends = boundaries.slice(), tails = [];
                if (noteSegmenter) {
                    var nextEnd = 0;
                    for (var segment of noteSegmenter.segment(node.data)) {
                        while (nextEnd < ends.length && ends[nextEnd] <= segment.index + segment.segment.length) {
                            if (segment.index > 0) tails.push(segment.index);
                            nextEnd++;
                        }
                        if (nextEnd === ends.length) break;
                    }
                } else ends.forEach(function (end) { tails.push(end - (node.data.codePointAt(end - 2) > 65535 ? 2 : 1)); });
                tails.forEach(function (tail) { if (tail > 0 && !boundaries.includes(tail)) boundaries.push(tail); });
                boundaries.sort(function (a,b) { return a-b; });
                parts.forEach(function (part) {
                    var from = part[0];
                    boundaries.filter(function (end) { return end > from && end <= part[1]; }).forEach(function (end) {
                        divided.push([from, end, part[2]]); from = end;
                    });
                    if (from < part[1]) divided.push([from, part[1], part[2]]);
                }); parts = divided;
            }
            if (!noteOffsets.size && !parts.some(function (part) { return part[2] >= 0 && Object.keys(currentStyles[part[2]]).length; })) { expanded.push(entry); return; }
            var wrapper = document.createElementNS(svgText ? 'http://www.w3.org/2000/svg' : 'http://www.w3.org/1999/xhtml',
                svgText ? 'tspan' : 'ng-char-runs');
            var svgDefaults = 'all:unset!important;direction:inherit!important;unicode-bidi:normal!important;';
            if (svgText) wrapper.style.cssText = svgDefaults;
            wrapper.setAttribute('data-ng-style-fragments', '');
            parts.forEach(function (part) {
                var text = document.createTextNode(node.data.slice(part[0], part[1]));
                var piece = { node: text, runs: sliceRuns(entry.runs, part[0], part[1]) };
                expanded.push(piece); nodeEntries.set(text, piece);
                function append(child) {
                    if (!noteOffsets.has(part[1]) || !noteStyle) { wrapper.appendChild(child); return; }
                    if (svgText) {
                        var anchor = document.createElementNS(wrapper.namespaceURI, 'tspan');
                        anchor.style.cssText = svgDefaults;
                        anchor.appendChild(child); wrapper.appendChild(anchor);
                        noteElements.push(Object.assign({ node: anchor, svg: true }, noteOffsets.get(part[1])));
                        return;
                    }
                    var anchor = document.createElementNS(wrapper.namespaceURI, 'ng-note-anchor');
                    anchor.style.setProperty('white-space', 'nowrap', 'important');
                    var pin = document.createElementNS(wrapper.namespaceURI, 'ng-note-pin');
                    pin.setAttribute('aria-hidden', 'true'); pin.style.cssText = 'user-select:none!important;pointer-events:none!important;' +
                        'display:inline-block!important;vertical-align:text-bottom!important;' +
                        'inline-size:' + (noteStyle.gap + noteStyle.size + noteStyle.trailing) + 'px!important;' +
                        'block-size:' + noteStyle.size + 'px!important;';
                    anchor.appendChild(child); anchor.appendChild(pin); wrapper.appendChild(anchor);
                    noteElements.push(Object.assign({ node: pin }, noteOffsets.get(part[1])));
                }
                var properties = currentStyles[part[2]];
                if (!properties) { append(text); return; }
                if (properties.titleSegment != null && titleBreaks.has(properties.titleSegment) &&
                    piece.runs.some(function (r) { return r[2] === titleSegments[properties.titleSegment].from; })) {
                    wrapper.appendChild(document.createElementNS(wrapper.namespaceURI, 'br')); titleBreaks.delete(properties.titleSegment);
                }
                var span = document.createElementNS(wrapper.namespaceURI, svgText ? 'tspan' : 'ng-char-style');
                if (svgText) span.style.cssText = svgDefaults;
                span.setAttribute('data-ng-char-style', part[2]);
                span.setAttribute('data-ng-rule-range', rangeAt(piece.runs[0] && piece.runs[0][2]));
                function css(name, value) { if (value != null) span.style.setProperty(name, String(value), 'important'); }
                css('color', properties.color);
                if (svgText) css('fill', properties.color);
                // The native renderer paints the image after its base colour.
                // Put that colour behind border-image, not on top of it in the child span.
                if (!properties.nineSlice && !properties.imageBackground) css('background-color', properties.background);
                css('font-weight', properties.weight);
                if (properties.size != null) css('font-size', properties.size + 'px');
                if (properties.lineHeight != null) css('line-height', properties.lineHeight + 'px');
                var loaded = fontCache.get(properties.font);
                if (loaded) css('font-family', loaded.family);
                if (properties.italic != null) css('font-style', properties.italic ? 'italic' : 'normal');
                if (properties.underline >= 1 && properties.underline <= 4) {
                    css('text-decoration-line', 'underline');
                    css('text-decoration-style', ['', 'solid', 'dashed', 'wavy', 'double'][properties.underline]);
                    css('text-decoration-color', properties.lineColor || properties.color || 'currentColor');
                    css('text-decoration-thickness', properties.lineWidth + 'px');
                    css('text-underline-offset', properties.lineOffset + 'px');
                    css('text-decoration-skip-ink', 'none');
                }
                span.appendChild(text); append(span);
                styleElements.push({ node: span, style: part[2], range: rangeAt(piece.runs[0] && piece.runs[0][2]),
                    from: piece.runs.length ? piece.runs[0][2] : -1,
                    to: piece.runs.length ? piece.runs[piece.runs.length - 1][3] : -1 });
            });
            node.replaceWith(wrapper); styledNodes.push({ node: node, wrapper: wrapper });
        });
        entries = expanded;
    }
    // Only DOM measurement/fragmentation lives here. Cut positions and corner scaling
    // come from ReadNineSliceGeometry through the publication's native resource gate.
    var imageParents = new Map(), imagePlans = new Map(), imageRevision = 0, svgImages = [];
    var lineStyleElements = null;
    function indexEntries() {
        nodeEntries = new WeakMap(); entries.forEach(function (entry) { nodeEntries.set(entry.node, entry); });
    }
    function clearLineBoxes() {
        wordWiseRectsCache = null;
        var restored = window.__ngEpubLines && window.__ngEpubLines.clear();
        if (!restored) return false;
        entries = restored; styleElements = lineStyleElements; lineStyleElements = null; indexEntries();
        return true;
    }
    function justifyLines(value) {
        clearLineBoxes();
        if (!window.__ngEpubLines) throw new Error('EPUB 行排版组件未加载');
        var result = window.__ngEpubLines.apply(value, entries);
        if (!result.shifted) return result;
        lineStyleElements = styleElements; entries = result.entries; indexEntries();
        var byElement = new Map();
        entries.forEach(function (entry) {
            var el = entry.node.parentElement.closest('[data-ng-char-style]');
            if (!el || !entry.runs.length) return;
            var from = entry.runs[0][2], to = entry.runs[entry.runs.length-1][3], item = byElement.get(el);
            if (item) { item.from = Math.min(item.from, from); item.to = Math.max(item.to, to); }
            else byElement.set(el, {node:el,style:+el.getAttribute('data-ng-char-style'),range:+el.getAttribute('data-ng-rule-range'),from:from,to:to});
        });
        styleElements = Array.from(byElement.values());
        return result;
    }
    function clearStyleImages() {
        clearLineBoxes();
        imageRevision++;
        svgImages.forEach(function (node) { node.remove(); }); svgImages = [];
        imageParents.forEach(function (children, parent) { parent.replaceChildren.apply(parent, children); });
        imageParents.clear();
    }
    function saveImageTree(element) {
        if (imageParents.has(element)) return;
        imageParents.set(element, Array.from(element.childNodes));
        Array.from(element.children).forEach(saveImageTree);
    }
    function imageBlock(element) {
        var parent = element.parentElement;
        while (parent && parent !== document.body && /^(inline|contents)$/.test(window.getComputedStyle(parent).display)) parent = parent.parentElement;
        return parent;
    }
    function nineMetrics(element) {
        var css = window.getComputedStyle(element), vertical = /^(vertical|sideways)/.test(css.writingMode);
        var scale = bodyTransform().scale, boxes = Array.from(element.getClientRects());
        var range = document.createRange(); range.selectNodeContents(element);
        var before = 0, after = 0, baseHeight = 0;
        boxes.forEach(function (box) { baseHeight = Math.max(baseHeight, vertical ? box.width : box.height); });
        // Inline element boxes use the primary font's metrics; CJK, bold and fallback
        // glyphs can extend beyond them. Keep that difference inside the image centre.
        Array.from(range.getClientRects()).forEach(function (glyph) {
            var nearest, distance = Infinity;
            boxes.forEach(function (box) {
                var d = Math.abs(box.left + box.right - glyph.left - glyph.right) + Math.abs(box.top + box.bottom - glyph.top - glyph.bottom);
                if (d < distance) { distance = d; nearest = box; }
            });
            if (!nearest) return;
            before = Math.max(before, vertical ? nearest.left - glyph.left : nearest.top - glyph.top);
            after = Math.max(after, vertical ? glyph.right - nearest.right : glyph.bottom - nearest.bottom);
        });
        var probe = document.createElementNS(element.namespaceURI, 'span');
        probe.textContent = 'M';
        probe.style.cssText = 'position:absolute!important;display:inline-block!important;visibility:hidden!important;' +
            'padding:0!important;margin:0!important;border:0!important;white-space:pre!important;font:inherit!important;line-height:inherit!important;';
        element.appendChild(probe);
        var line = parseFloat(css.lineHeight) || (vertical ? probe.offsetWidth : probe.offsetHeight);
        probe.remove();
        return { height: (baseHeight + before + after) / scale, baseHeight: baseHeight / scale, line: line, vertical: vertical,
            before: before / scale, after: after / scale };
    }
    function prepareStyleImages(cachedOnly) {
        clearStyleImages();
        var revision = imageRevision, groups = [], density = window.devicePixelRatio || 1;
        styleElements.forEach(function (item) {
            var style = currentStyles[item.style], nine = !!style.nineSlice;
            var image = style.nineSlice || style.imageBackground;
            var svg = item.node.namespaceURI === 'http://www.w3.org/2000/svg';
            if ((!image && !(svg && style.background)) ||
                window.getComputedStyle(item.node).visibility !== 'visible' || !item.node.getClientRects().length) return;
            if (svg) {
                var text = item.node.closest('text'); if (!text) return;
                var bounds = item.node.getBBox(); if (!(bounds.width > 0 && bounds.height > 0)) return;
                var lastSvg = groups[groups.length-1], matrix = item.node.getCTM();
                var transformKey = [matrix.a,matrix.b,matrix.c,matrix.d,matrix.e,matrix.f].join('/');
                if (lastSvg && lastSvg.svg && lastSvg.block === text && lastSvg.range === item.range && lastSvg.to === item.from &&
                    lastSvg.image === image && lastSvg.background === style.background && lastSvg.transformKey === transformKey &&
                    Math.abs(lastSvg.bounds.y-bounds.y)<=1 && Math.abs(lastSvg.bounds.height-bounds.height)<=1 &&
                    bounds.x <= lastSvg.bounds.x+lastSvg.bounds.width+.5 && bounds.x+bounds.width >= lastSvg.bounds.x-.5) {
                    var right = Math.max(lastSvg.bounds.x+lastSvg.bounds.width,bounds.x+bounds.width);
                    var bottom = Math.max(lastSvg.bounds.y+lastSvg.bounds.height,bounds.y+bounds.height);
                    lastSvg.bounds.y=Math.min(lastSvg.bounds.y,bounds.y);lastSvg.bounds.height=bottom-lastSvg.bounds.y;
                    lastSvg.bounds.x=Math.min(lastSvg.bounds.x,bounds.x);lastSvg.bounds.width=right-lastSvg.bounds.x;
                    lastSvg.last=item.node;lastSvg.to=item.to;lastSvg.nodes.push(item.node);return;
                }
                groups.push({ svg: true, first: item.node, last: item.node, block: text, bounds: bounds,
                    image: image, nine: nine, inset: style.imageInset, background: style.background, nodes: [item.node],
                    range:item.range,from:item.from,to:item.to,transformKey:transformKey });
                return;
            }
            if (item.node.namespaceURI !== 'http://www.w3.org/1999/xhtml') return;
            var block = imageBlock(item.node); if (!block) return;
            var css = window.getComputedStyle(item.node), font = [css.fontFamily, css.fontSize, css.lineHeight, css.writingMode].join('/');
            var last = groups[groups.length - 1];
            if (last && last.image === image && last.nine === nine && last.block === block && last.range === item.range && last.to === item.from && last.font === font) {
                last.last = item.node; last.to = item.to; last.nodes.push(item.node);
            } else groups.push({ first: item.node, last: item.node, block: block, image: image, nine: nine, inset: style.imageInset,
                from: item.from, to: item.to, range: item.range, font: font, background: currentStyles[item.style].background, nodes: [item.node] });
        });
        var work = groups.map(function (group) {
            var vertical = /^(vertical|sideways)/.test(window.getComputedStyle(group.first).writingMode);
            var metrics = group.svg ? { height: vertical ? group.bounds.width : group.bounds.height,
                width: vertical ? group.bounds.height : group.bounds.width, line: vertical ? group.bounds.width : group.bounds.height,
                vertical: vertical } : group.nine ? nineMetrics(group.first) : { height: 0, line: 0 };
            if (group.nine && !group.svg) group.nodes.slice(1).forEach(function (node) {
                var other = nineMetrics(node);
                metrics.baseHeight = Math.max(metrics.baseHeight, other.baseHeight);
                metrics.before = Math.max(metrics.before, other.before); metrics.after = Math.max(metrics.after, other.after);
                metrics.line = Math.max(metrics.line, other.line);
            });
            if (group.nine && !group.svg) metrics.height = metrics.baseHeight + metrics.before + metrics.after;
            if (group.nine && !(metrics.height > 0 && metrics.line > 0)) return Promise.resolve(null);
            if (group.svg && !group.image) { apply(null); return Promise.resolve(null); }
            var base = new URL(group.image, document.baseURI);
            var allowed = group.nine ? /^\/__ng_style_nine\/[a-f0-9]{64}$/.test(base.pathname)
                : /^\/__ng_style_background\/[a-f0-9]{64}\/image$/.test(base.pathname);
            if (base.origin !== window.location.origin || !allowed) throw new Error('字样式图片来源无效');
            var url = group.nine ? base.href + '/layout?height=' + metrics.height.toFixed(4) + '&line=' + metrics.line.toFixed(4) +
                '&density=' + density + '&vertical=' + (metrics.vertical ? 1 : 0) +
                (group.svg ? '&boxWidth=' + metrics.width.toFixed(4) : '') : base.href + '?density=' + density;
            var entry = imagePlans.get(url);
            if (!entry && cachedOnly) return Promise.resolve(null);
            if (!entry) {
                entry = { value: null };
                var request = group.nine ? fetch(url, { credentials: 'omit', redirect: 'error' }).then(function (response) {
                    if (!response.ok) throw new Error('九宫格尺寸加载失败');
                    return response.json();
                }) : Promise.resolve({ image: url });
                entry.promise = request.then(function (plan) {
                    var resource = new URL(plan.image, document.baseURI);
                    if (group.nine && (resource.origin !== base.origin || resource.pathname !== base.pathname + '/image' ||
                        !Array.isArray(plan.border) || plan.border.length !== 4 || !plan.border.every(function (n) { return Number.isFinite(n) && n >= 0; }) ||
                        !Number.isFinite(plan.lineHeight) || plan.lineHeight <= 0 ||
                        !Array.isArray(plan.slice) || plan.slice.length !== 4 || !plan.slice.every(function (n) { return Number.isInteger(n) && n >= 0; }) ||
                        !Number.isFinite(plan.inset) || plan.inset < 0)) throw new Error('九宫格绘制参数无效');
                    return new Promise(function (resolve, reject) {
                        var image = new window.Image(); image.onload = function () { entry.value = plan; resolve(plan); };
                        image.onerror = function () { reject(new Error('九宫格图片加载失败')); }; image.src = resource.href;
                    });
                });
                imagePlans.set(url, entry);
                if (imagePlans.size > 128) imagePlans.delete(imagePlans.keys().next().value);
            }
            function apply(plan) {
                if (revision !== imageRevision || !group.first.isConnected || !group.last.isConnected) return null;
                if (group.svg) {
                    // Keep fixed SVG coordinates. A text-free HTML box paints the existing
                    // native image plan behind the label, bounded by its authored glyph box.
                    var outer = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
                    outer.setAttribute('data-ng-svg-background', ''); outer.setAttribute('aria-hidden', 'true');
                    var box = group.bounds, parent = group.block.parentElement;
                    var transform = parent.getCTM().inverse().multiply(group.first.getCTM());
                    outer.setAttribute('transform', 'matrix(' + [transform.a,transform.b,transform.c,transform.d,transform.e,transform.f].join(' ') + ')');
                    ['x','y','width','height'].forEach(function (name) { outer.setAttribute(name, box[name]); });
                    outer.style.setProperty('pointer-events', 'none', 'important');
                    var paint = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
                    var css = { all:'initial', display:'block', margin:'0', padding:'0', border:'0', width:'100%', height:'100%',
                        'box-sizing':'border-box', 'background-color':group.background || 'transparent', 'pointer-events':'none' };
                    if (group.nine) Object.assign(css, { border:'solid transparent',
                        'border-width':plan.border.map(function (n) { return n + 'px'; }).join(' '),
                        'border-image-source':'url(' + JSON.stringify(new URL(plan.image, document.baseURI).href) + ')',
                        'border-image-slice':plan.slice.join(' ') + ' fill', 'border-image-width':'1', 'border-image-outset':'0', 'border-image-repeat':'stretch' });
                    else if (plan) Object.assign(css, { 'background-image':'url(' + JSON.stringify(plan.image) + ')',
                        'background-size':'100% 100%', 'background-repeat':'no-repeat' });
                    Object.keys(css).forEach(function (name) { paint.style.setProperty(name, css[name], 'important'); });
                    outer.appendChild(paint); parent.insertBefore(outer, group.block); svgImages.push(outer);
                    return null;
                }
                // extractContents moves the fully selected mapped text nodes. Snapshots
                // restore original author element identities, including partial boundary tags.
                var range = document.createRange();
                range.setStartBefore(group.first.closest('ng-note-anchor') || group.first);
                range.setEndAfter(group.last.closest('ng-note-anchor') || group.last);
                var parent = range.commonAncestorContainer;
                if (parent.nodeType !== Node.ELEMENT_NODE) parent = parent.parentElement;
                saveImageTree(parent);
                var wrapper = document.createElementNS(group.first.namespaceURI, group.nine ? 'ng-nine-slice' : 'ng-image-background');
                var properties = { display: 'inline', margin: '0', padding: '0', border: '0',
                    'background-color': group.background || 'transparent', 'background-clip': 'content-box',
                    'box-decoration-break': 'clone', '-webkit-box-decoration-break': 'clone',
                    'box-sizing': 'content-box', 'line-height': 'inherit', 'vertical-align': 'baseline' };
                if (group.nine) Object.assign(properties, { border: 'solid transparent',
                    'border-width': plan.border.map(function (n) { return n + 'px'; }).join(' '),
                    'border-image-source': 'url(' + JSON.stringify(new URL(plan.image, document.baseURI).href) + ')',
                    'border-image-slice': plan.slice.join(' ') + ' fill',
                    'border-image-width': '1',
                    'border-image-outset': '0', 'border-image-repeat': 'stretch',
                    'line-height': plan.lineHeight + 'px' });
                if (group.nine) {
                    properties[metrics.vertical ? 'padding-top' : 'padding-left'] = plan.inset + 'px';
                    properties[metrics.vertical ? 'padding-bottom' : 'padding-right'] = plan.inset + 'px';
                    properties[metrics.vertical ? 'padding-left' : 'padding-top'] = metrics.before + 'px';
                    properties[metrics.vertical ? 'padding-right' : 'padding-bottom'] = metrics.after + 'px';
                } else {
                    if (!Number.isFinite(group.inset) || group.inset < 0) throw new Error('图片背景边界无效');
                    Object.assign(properties, { 'background-image': 'url(' + JSON.stringify(plan.image) + ')',
                        'background-size': '100% calc(100% - ' + (group.inset * 2) + 'px)',
                        'background-position': 'center', 'background-repeat': 'no-repeat' });
                }
                Object.keys(properties).forEach(function (name) { wrapper.style.setProperty(name, properties[name], 'important'); });
                wrapper.appendChild(range.extractContents()); range.insertNode(wrapper);
                return null;
            }
            if (entry.value) { apply(entry.value); return Promise.resolve(null); }
            return entry.promise.then(apply).catch(function () { imagePlans.delete(url); return '部分图片背景无法加载'; });
        });
        return Promise.all(work).then(function (warnings) { return warnings.filter(Boolean); });
    }
    function clearUnderlines() {
        underlineFragments = [];
        if (underlineLayer) underlineLayer.replaceChildren();
    }
    function ruleUnderlineRects() {
        var result = [];
        styleElements.forEach(function (item) {
            if (!(currentStyles[item.style].nativeUnderline > 0)) return;
            var range = document.createRange(); range.selectNodeContents(item.node);
            Array.from(range.getClientRects()).forEach(function (rect) { if (rect.width > 0 && rect.height > 0) result.push(rect); });
        });
        return result;
    }
    function bodyTransform() {
        // The reader moves pages with a single body translation. Cache text geometry on
        // layout, then apply that translation without measuring every match on each turn.
        var css = window.getComputedStyle(document.body).transform;
        var match = /^matrix\(([^)]+)\)$/.exec(css), values = match && match[1].split(',').map(Number);
        return values && values.length === 6 && values.every(Number.isFinite)
            ? { x: values[4], y: values[5], scale: Math.abs(values[3]) || 1 } : { x: 0, y: 0, scale: 1 };
    }
    function prepareUnderlines() {
        clearUnderlines();
        if (!document.body) return;
        var transform = bodyTransform();
        styleElements.forEach(function (item) {
            var value = currentStyles[item.style];
            if (value.underline !== 5 || !value.linePath) return;
            var css = window.getComputedStyle(item.node);
            if (css.visibility !== 'visible') return;
            var vertical = /^(vertical|sideways)/.test(css.writingMode);
            var color = value.lineColor || value.color || css.color;
            Array.from(item.node.getClientRects()).forEach(function (rect) {
                if (rect.width <= 0 || rect.height <= 0) return;
                var box = { left: rect.left - transform.x, right: rect.right - transform.x,
                    top: rect.top - transform.y, bottom: rect.bottom - transform.y };
                var last = underlineFragments[underlineFragments.length - 1];
                // Adjacent authored tags may split one matched run. Join only touching
                // fragments on the same visual line, never separate matches or pages.
                if (last && last.range === item.range && last.style === item.style &&
                    last.vertical === vertical && last.color === color && (vertical
                        ? Math.abs(last.left - box.left) < 1 && Math.abs(last.right - box.right) < 1 &&
                            box.top <= last.bottom + 1 && box.bottom >= last.top - 1
                        : Math.abs(last.top - box.top) < 1 && Math.abs(last.bottom - box.bottom) < 1 &&
                            box.left <= last.right + 1 && box.right >= last.left - 1)) {
                    last.left = Math.min(last.left, box.left); last.right = Math.max(last.right, box.right);
                    last.top = Math.min(last.top, box.top); last.bottom = Math.max(last.bottom, box.bottom);
                } else underlineFragments.push(Object.assign(box, { range: item.range, style: item.style,
                    vertical: vertical, color: color, scale: transform.scale }));
            });
        });
    }
    function paintUnderlines(visible) {
        if (underlineLayer) underlineLayer.replaceChildren();
        if (!underlineFragments.length || !document.body) return;
        if (!underlineLayer) {
            underlineLayer = document.createElementNS('http://www.w3.org/1999/xhtml', 'ng-char-underlines');
            underlineLayer.setAttribute('aria-hidden', 'true');
            var layerStyle = { position: 'fixed', inset: '0', 'pointer-events': 'none',
                'z-index': '2147483639', overflow: 'hidden', 'user-select': 'none' };
            Object.keys(layerStyle).forEach(function (name) { underlineLayer.style.setProperty(name, layerStyle[name], 'important'); });
            document.documentElement.appendChild(underlineLayer);
        }
        var transform = bodyTransform(), density = window.devicePixelRatio || 1;
        underlineFragments.forEach(function (fragment) {
            var rect = { left: fragment.left + transform.x, right: fragment.right + transform.x,
                top: fragment.top + transform.y, bottom: fragment.bottom + transform.y };
            rect.width = rect.right - rect.left; rect.height = rect.bottom - rect.top;
            if (!visible(rect)) return;
            var value = currentStyles[fragment.style], scale = fragment.scale;
            var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            var line = document.createElementNS(svg.namespaceURI, 'path');
            // Match TextLine.drawSvgUnderline: x spans the run; y=50 is the
            // underline baseline in native pixels. Width/offset remain dp values.
            var thickness = 100 / density * scale, offset = (value.lineOffset || 0) * scale;
            var length = fragment.vertical ? rect.height : rect.width;
            svg.setAttribute('viewBox', '0 0 100 100'); svg.setAttribute('preserveAspectRatio', 'none');
            svg.setAttribute('width', String(length)); svg.setAttribute('height', String(thickness));
            var properties = { position: 'absolute', display: 'block', overflow: 'visible', margin: '0', padding: '0', border: '0',
                'pointer-events': 'none',
                'max-width': 'none', 'max-height': 'none', 'min-width': '0', 'min-height': '0',
                left: (fragment.vertical ? rect.left - offset + thickness / 2 : rect.left) + 'px',
                top: (fragment.vertical ? rect.top : rect.bottom + offset - thickness / 2) + 'px',
                width: length + 'px', height: thickness + 'px',
                transform: fragment.vertical ? 'rotate(90deg)' : 'none', 'transform-origin': '0 0' };
            Object.keys(properties).forEach(function (name) { svg.style.setProperty(name, properties[name], 'important'); });
            line.setAttribute('d', value.linePath); line.style.setProperty('fill', 'none', 'important');
            line.style.setProperty('stroke', fragment.color, 'important');
            line.style.setProperty('stroke-width', String(value.lineWidth * density), 'important');
            line.style.setProperty('stroke-dasharray', 'none', 'important');
            line.style.setProperty('pointer-events', 'none', 'important');
            svg.appendChild(line); underlineLayer.appendChild(svg);
        });
    }
    function loadFonts() {
        if (fontPromise) return fontPromise;
        var mine = styleRevision, styles = currentStyles, requestedFonts = new Map();
        fontPromise = Promise.all(styles.map(async function (style, index) {
            if (!style.font || !styleElements.some(function (item) { return item.style === index; })) return null;
            var url = new URL(style.font, document.baseURI);
            if (url.origin !== window.location.origin || !url.pathname.startsWith('/__ng_style_font/')) throw new Error('字样式字体来源无效');
            var family = 'NGCharStyle' + index;
            var face = fontCache.get(style.font) || requestedFonts.get(style.font) ||
                new window.FontFace(family + (fontFaces.length ? '-' + styleRevision : ''), 'url(' + JSON.stringify(url.href) + ')');
            requestedFonts.set(style.font, face);
            try {
                await face.load();
                if (styleRevision !== mine) return null;
                document.fonts.add(face); if (!fontFaces.includes(face)) fontFaces.push(face); fontCache.set(style.font, face);
                styleElements.filter(function (item) { return item.style === index; }).forEach(function (item) {
                    item.node.style.setProperty('font-family', face.family, 'important');
                });
                return null;
            } catch (_) {
                if (styleRevision !== mine) return null;
                styleElements.filter(function (item) { return item.style === index; }).forEach(function (item) {
                    item.node.style.removeProperty('font-weight'); item.node.style.removeProperty('font-style');
                });
                return '部分字样式字体无法加载';
            }
         })).then(function (warnings) {
            if (styleRevision === mine) fontCache.forEach(function (face, path) {
                if (!styles.some(function (style) { return style.font === path; })) {
                    document.fonts.delete(face); fontCache.delete(path); fontFaces = fontFaces.filter(function (item) { return item !== face; });
                }
            });
            return warnings.filter(Boolean);
        });
        return fontPromise;
    }
    function element(id) {
        if (!/^[0-9]+-[0-9]+$/.test(id)) throw new Error('EPUB 节点标识无效');
        var result = document.querySelector('[' + attribute + '="' + id + '"]');
        if (!result) throw new Error('EPUB 原文节点不存在：' + id);
        return result;
    }
    function hide(node) {
        if (!hidden.has(node)) hidden.set(node, [node.style.getPropertyValue('display'), node.style.getPropertyPriority('display')]);
        node.style.setProperty('display', 'none', 'important');
    }
    function apply(value, isNight) {
        if (!value || value.version !== 1 || !Array.isArray(value.nodes)) throw new Error('EPUB 正文位置数据无效');
        nightMode = !!isNight;
        if (key === value.key) return updateStyles(value).changed;
        wordWiseRectsCache = null;
        clearWordWise();
        clearStyleImages(); imagePlans.clear();
        noteMarkers = []; noteElements = []; noteStyle = null; noteSignature = '[[],null]';
        titleSegments = []; titleStyle = null; titleSignature = '[[],null]';
        clearUnderlines();
        styledNodes.forEach(function (item) { item.wrapper.replaceWith(item.node); }); styledNodes = []; styleElements = [];
        fontFaces.forEach(function (face) { document.fonts.delete(face); }); fontFaces = []; fontCache.clear();
        fontPromise = null;
        original.forEach(function (text, node) { node.data = text; });
        hidden.forEach(function (display, node) { if (display[0]) node.style.setProperty('display', display[0], display[1]); else node.style.removeProperty('display'); });
        hidden.clear(); generated.forEach(function (node) { node.remove(); }); generated = [];
        whitespace.forEach(function (value, node) {
            if (value[0]) node.style.setProperty('white-space', value[0], value[1]); else node.style.removeProperty('white-space');
        }); whitespace.clear();
        entries = []; media = []; nodeEntries = new WeakMap();
        value.nodes.forEach(function (item) {
            var node;
            if (item.beforeMedia) {
                var target = element(item.beforeMedia);
                node = document.createTextNode(''); target.parentNode.insertBefore(node, target); generated.push(node);
            } else {
                node = Array.from(element(item.element).childNodes).filter(function (child) { return child.nodeType === Node.TEXT_NODE; })[item.ordinal];
                // HTML tokenization normalizes CRLF/CR before creating text nodes. Restore the
                // recorded text below, keeping its exact UTF-16 coordinates for native content.
                if (!node || node.data !== item.original.replace(/\r\n?/g, '\n')) throw new Error('EPUB 原文节点内容不一致：' + item.element);
                if (!original.has(node)) original.set(node, node.data);
            }
            if (typeof item.text !== 'string' || !Array.isArray(item.runs)) throw new Error('EPUB 正文范围无效');
            var previous = 0;
            item.runs.forEach(function (r) {
                if (r.length !== 5 || !r.every(Number.isInteger) || r[0] < previous || r[1] <= r[0] ||
                    r[1] > item.text.length || r[2] < 0 || r[3] < r[2] ||
                    (r[4] !== 0 && r[4] !== 1) || r[4] === 1 && r[3] - r[2] !== r[1] - r[0])
                    throw new Error('EPUB 正文范围越界');
                previous = r[1];
            });
            node.data = item.text;
            if (item.preserveBreaks) {
                var parent = node.parentElement;
                if (!whitespace.has(parent)) whitespace.set(parent, [parent.style.getPropertyValue('white-space'), parent.style.getPropertyPriority('white-space')]);
                parent.style.setProperty('white-space', 'pre-line', 'important');
            }
            var entry = { node: node, runs: item.runs, element: item.element, ordinal: item.ordinal };
            entries.push(entry); nodeEntries.set(node, entry);
        });
        (value.excluded || []).forEach(function (id) { hide(element(id)); });
        (value.media || []).forEach(function (item) {
            var node = element(item.element);
            if (!item.visible) hide(node);
            if (item.start >= 0 && item.end > item.start) media.push({ node: node, start: item.start, end: item.end });
        });
        key = value.key; contentLength = value.text.length; baseEntries = entries.slice();
        applyStyles(value);
        return true;
    }
    function offset(node, at, after) {
        var entry = nodeEntries.get(node);
        if (entry) {
            var runs = entry.runs;
            if (after) {
                for (var i = runs.length - 1; i >= 0; i--) {
                    var r = runs[i];
                    if (at > r[0]) return r[4] ? r[2] + Math.min(at, r[1]) - r[0] : r[3];
                }
            } else {
                for (var j = 0; j < runs.length; j++) {
                    var s = runs[j];
                    if (at < s[1]) return s[4] ? s[2] + Math.max(at, s[0]) - s[0] : s[2];
                }
            }
            if (runs.length) return after ? runs[0][2] : runs[runs.length - 1][3];
        }
        var item = media.find(function (value) { return value.node === node || value.node.contains(node) || node.contains(value.node); });
        return item ? (after ? item.end : item.start) : null;
    }
    var pointEntries = null, pointMedia = null, pointTree = null;
    function point(position, after) {
        // Entries are replaced whenever styles, notes, titles or line boxes rebuild text nodes.
        // An interval tree also handles reordered/overlapping edit runs; DOM order is the tie-break.
        if (pointEntries !== entries || pointMedia !== media) {
            pointEntries = entries; pointMedia = media;
            var items = [], order = 0;
            entries.forEach(function (entry) { entry.runs.forEach(function (r) {
                items.push({ node: entry.node, run: r, start: r[2], end: r[3], order: order++ });
            }); });
            media.forEach(function (item) { items.push({ node: item.node, start: item.start, end: item.end, order: order++ }); });
            items.sort(function (a, b) { return a.start - b.start || a.order - b.order; });
            function tree(from, to) {
                if (from >= to) return null;
                var middle = (from + to) >>> 1, item = items[middle];
                var left = tree(from, middle), right = tree(middle + 1, to);
                return { item: item, left: left, right: right, start: items[from].start,
                    end: Math.max(item.end, left ? left.end : -Infinity, right ? right.end : -Infinity),
                    order: Math.min(item.order, left ? left.order : Infinity, right ? right.order : Infinity) };
            }
            pointTree = tree(0, items.length);
        }
        var closest = null, distance = Infinity, bestOrder = Infinity;
        function lower(node) { return !node ? Infinity : Math.max(0, node.start - position, position - node.end); }
        function visit(node) {
            if (!node) return;
            var bound = lower(node);
            if (bound > distance || bound === distance && node.order >= bestOrder) return;
            var item = node.item, r = item.run;
            var delta = r && after ? (position <= item.start ? item.start - position + 1 : position > item.end ? position - item.end : 0)
                : (position < item.start ? item.start - position : position >= item.end ? position - item.end + 1 : 0);
            if (delta < distance || delta === distance && item.order < bestOrder) {
                distance = delta; bestOrder = item.order;
                closest = { node: item.node, offset: !r ? 0 : r[4] ? r[0] + Math.max(0, Math.min(r[1] - r[0], position - r[2])) : r[after ? 1 : 0] };
            }
            var first = node.left, second = node.right;
            if (lower(second) < lower(first) || lower(second) === lower(first) && second && (!first || second.order < first.order)) {
                first = node.right; second = node.left;
            }
            visit(first); visit(second);
        }
        visit(pointTree);
        return closest;
    }
    function selection(range) {
        if (!range) return null;
        var from = offset(range.startContainer, range.startOffset, false);
        var to = offset(range.endContainer, range.endOffset, true);
        return Number.isInteger(from) && Number.isInteger(to) && to > from ? { from: from, to: to } : null;
    }
    function setAloud(value) {
        if (JSON.stringify(aloud) === JSON.stringify(value)) return false;
        aloud = value;
        if (!lastStylePayload) return false;
        resetStyles(); applyStyles(lastStylePayload); prepareStyleImages(true); return true;
    }
    window.__ngEpubContent = Object.freeze({ apply: apply, loadFonts: loadFonts, offset: offset, point: point, selection: selection,
        setAloud: setAloud,
        setNotes: setNotes, noteRects: noteRects,
        setWordWise: setWordWise, wordWiseWritingSupported: wordWiseWritingSupported,
        wordWiseRects: wordWiseRects, wordWiseCount: function () { return wordWiseFragments.length; },
        paintWordWise: paintWordWise, clearWordWise: clearWordWise, hitWordWise: hitWordWise,
        setTitleSegments: setTitleSegments, titleHeadings: titleHeadings,
        ruleUnderlineRects: ruleUnderlineRects,
        justifyLines: justifyLines, clearLineBoxes: clearLineBoxes,
        loadNotes: function () { return noteImage ? noteImage.promise : Promise.resolve([]); },
        prepareStyleImages: prepareStyleImages, clearStyleImages: clearStyleImages,
        key: function () { return key; }, updateStyles: updateStyles, prepareUnderlines: prepareUnderlines, paintUnderlines: paintUnderlines, clearUnderlines: clearUnderlines,
        nodes: function () { return entries.map(function (entry) { return entry.node; }); } });
})(window);
