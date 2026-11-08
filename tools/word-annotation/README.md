# Word annotation frequency asset

This directory holds the developer-side generator for the Word Wise fusion
prototype's frequency-rank prior. The raw source pages are **not** committed to
the repository; only the derived rank asset under
`app/src/main/assets/word_annotation/` is.

## Source

- Wiktionary: `Frequency_lists/English/Project_Gutenberg`
  <https://en.wiktionary.org/wiki/Wiktionary:Frequency_lists/English/Project_Gutenberg>
- Snapshot: **16 April 2006** (the `wiktionary_pg_2006_04_*` pages).
- It is a straight frequency count of the Project Gutenberg English texts then
  available: about 24,197 files and 1.7 billion words. It is pre-1923-heavy
  (`thy` is rank 280) and includes Project Gutenberg boilerplate (`Gutenberg` is
  rank 256, `license` 2,008, `project` 4,357).
- Counts in the source are per billion.

## License

- The Wiktionary page text is **CC BY-SA 4.0**:
  <https://creativecommons.org/licenses/by-sa/4.0/>.
- The underlying Project Gutenberg texts are public domain in the US.
- The generated rank asset keeps the source and license notice in its header
  and in `frequency_rank_2006_04.meta.json`. The repository itself is GPL-3.0;
  keep the data notice with the asset if it is redistributed.
- Do not use the convenience CSV copies floating around as the source of
  truth. The 20k CSV has duplicate rows with missing counts in its tail, and
  the 36k CSV repeats its header every 1,000 rows. Both are extracts of the
  same canonical page.

## Generate

The input directory must contain the four canonical pages:

```text
wiktionary_pg_2006_04_1-10000.wiki
wiktionary_pg_2006_04_10001-20000.wiki
wiktionary_pg_2006_04_20001-30000.wiki
wiktionary_pg_2006_04_30001-40000.wiki
```

Run from the repository root:

```bash
python3 -I tools/word-annotation/generate_frequency_rank.py \
    --input-dir ~/Downloads/words/gutenberg \
    --output app/src/main/assets/word_annotation/frequency_rank_2006_04.txt \
    --meta-output app/src/main/assets/word_annotation/frequency_rank_2006_04.meta.json
```

The script validates contiguous ranks, unique words, positive counts, and the
top-three words (`the`, `of`, `and`) before writing anything. It prints the row
count, first/last entries, and the SHA-256 of the word payload.

## Output format

`frequency_rank_2006_04.txt` starts with `#` comment lines, then one ranked
word per line. `WordFrequencyIndex.fromRankAssetLines` drops the comment/blank
lines and gives every remaining line a rank, including abbreviation/number
tokens the index will skip. That keeps the canonical rank positions stable.

`WordFrequencyAssets.VERSION` and `ASSET_PATH` must match the generated file.

## Caveats for the prior

- This is a **rank prior**, not a difficulty oracle and not a selector. The LLM
  still decides which words matter in context.
- The 2006/pre-1923 skew means archaic words rank high. The LLM selection is the
  safeguard; do not convert the rank into a hard filter.
- No lemmas, inflections, POS, or senses. English forms come from
  `WordAnnotationInflector`; Chinese stays exact-span only.
- If a newer Project Gutenberg snapshot is used, regenerate the asset and bump
  `WordFrequencyAssets.VERSION` (and the asset filename) so the list version is
  logged and cache/evaluation results remain attributable.
