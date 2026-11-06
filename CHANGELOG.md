# Changelog

All notable changes. The newest version is on top; the release workflow uses the section for the tag being released as the release notes.

## 2.0.0

The first release of the native app (Jetpack Compose, Material 3 Expressive). It installs over the old Flutter build and reads its notes.

### Notes
- Titles set in Google Sans Flex or Roboto Flex using the fonts' own axes: weight, width, optical size, stroke thickness, x-height, ascender, descender and figure height. No outlines, highlights or other effects on top.
- A title that morphs between the note's tile and its page.
- A photo's shape can overlap the title, by a sliver of the last line at most, so the title stays readable.
- Formatting: bullet and numbered lists (Enter continues a list, Enter on an empty item ends it, numbers renumber themselves), quote and pull quote, block indent, first-line indent, left/centre/right/justified, sub- and superscript, spaced caps, two heading sizes, a centred break.
- Undo and redo work for text, lists and formatting.
- The line being typed stays near the middle of the screen; there is room to scroll under it.
- Checklist progress is a Material 3 Expressive wavy ring with the count in the middle.

### Collections
- Film stack and vinyl shelf redrawn; the panel under the shelf shows the description.
- Books and songs show details in English, fetched once and kept: author, description, publisher, release date.

### Screens and artifacts
- Screenshot detail no longer prints all the text; weirdly cropped screenshots stay within their card.
- A photo with no clear subject becomes a stamp (the whole picture, cut to a shape), not a bad cut-out sticker.
- Stamps on canvases are dingbats in badges.
- Artifacts widget.
- Food and product artifacts show a facts panel.

### Canvases
- The hub and the board use the plain Material You surface; no gradients. Card rings match the ground exactly.

### App
- Narrower floating navigation bar; redesigned side menu.
- Adaptive launcher icon with a monochrome layer.
- Release build type; CI builds both APKs and a tag builds a GitHub release.
