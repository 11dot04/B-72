import 'dart:math';
import 'package:flutter/material.dart';

const kSans = 'GoogleSansFlex', kSerif = 'InstrumentSerif';

/// Google Sans Flex with all six axes: weight, width, optical size (follows the size),
/// roundness, grade and slant. Falls back to the system font if the file is missing.
TextStyle sans(double size, {Color? color, double wght = 600, double wdth = 100, double rond = 45, double grade = 0, double slnt = 0, double h = 1.1, double ls = 0}) => TextStyle(
      fontFamily: kSans,
      fontSize: size,
      height: h,
      letterSpacing: ls,
      color: color,
      fontVariations: [
        FontVariation('wght', wght),
        FontVariation('wdth', wdth),
        FontVariation('opsz', size.clamp(6.0, 144.0).toDouble()),
        FontVariation('ROND', rond),
        FontVariation('GRAD', grade),
        FontVariation('slnt', slnt),
      ],
    );

TextStyle serif(double size, {Color? color, bool italic = false, double h = 1.1}) =>
    TextStyle(fontFamily: kSerif, fontSize: size, height: h, color: color, fontStyle: italic ? FontStyle.italic : FontStyle.normal);

const _tail = {'a', 'an', 'the', 'of', 'in', 'on', 'at', 'to', 'for', 'and', 'or', 'with', 'as', 'by', 'from'}; // never end a line on these
const _lead = {'as', 'of', 'in', 'on', 'with', 'for', 'from', 'by', 'and', 'to'}; // good words to start a line with

/// Balanced line breaks that respect phrasing: "Motion Blur" / "as an Art Form".
List<String> breakTitle(String t, {int maxLines = 3}) {
  final w = t.trim().split(RegExp(r'\s+'));
  if (w.length < 3 || t.trim().length < 14) return [t.trim()];
  final n = t.length > 34 && w.length > 5 && maxLines >= 3 ? 3 : 2;
  var best = [t.trim()];
  var bc = 1e9;
  void tryCut(List<int> cuts) {
    final parts = <String>[];
    var s = 0;
    for (final c in [...cuts, w.length]) {
      parts.add(w.sublist(s, c).join(' '));
      s = c;
    }
    final mean = t.length / parts.length;
    var cost = 0.0;
    for (final p in parts) {
      cost += (p.length - mean).abs();
    }
    for (final c in cuts) {
      if (_tail.contains(w[c - 1].toLowerCase())) cost += 10;
      if (_lead.contains(w[c].toLowerCase())) cost -= 4;
    }
    if (cost < bc) {
      bc = cost;
      best = parts;
    }
  }

  if (n == 2) {
    for (var i = 1; i < w.length; i++) {
      tryCut([i]);
    }
  } else {
    for (var i = 1; i < w.length - 1; i++) {
      for (var j = i + 1; j < w.length; j++) {
        tryCut([i, j]);
      }
    }
  }
  return best;
}

int vOf(String id) => id.codeUnits.fold(0, (a, b) => a + b) % 6;

/// How each line of a title is styled in each variant: size multiplier, weight, roundness, slant.
(double, double, double, double) _line(int v, int i, double wght) => switch (v % 6) {
      1 => i.isEven ? (1.12, 850.0, 20.0, 0.0) : (.78, 320.0, 100.0, 0.0), // heavy / airy contrast
      2 => (.8, 720.0, 100.0, 0.0), // wide and round
      3 => (1.3, 900.0, 0.0, 0.0), // tall condensed poster
      4 => (.95, 300.0, 100.0, -10.0), // light italic
      5 => i.isEven ? (1.0, 800.0, 40.0, 0.0) : (.85, 400.0, 80.0, -10.0), // staggered
      _ => (1.0, wght, 45.0, 0.0),
    };

/// Picks the width axis (and, if needed, the size) so a line fills [w] exactly.
(double, double) _fit(String line, double w, double size, double wght, double rond, double slnt, double grade, bool grow) {
  double width(double s, double wd) =>
      (TextPainter(text: TextSpan(text: line, style: sans(s, wght: wght, wdth: wd, rond: rond, slnt: slnt, grade: grade)), textDirection: TextDirection.ltr)..layout()).width;
  var lo = 25.0, hi = 151.0;
  final widest = width(size, hi);
  if (widest < w) return grow ? (min(size * w / max(widest, 1), size * 2.4), hi) : (size, hi);
  final narrowest = width(size, lo);
  if (narrowest > w) return (size * w / narrowest, lo);
  for (var i = 0; i < 9; i++) {
    final mid = (lo + hi) / 2;
    if (width(size, mid) > w) {
      hi = mid;
    } else {
      lo = mid;
    }
  }
  return (size, lo);
}

/// A title that always fills its width: balanced lines, with the width axis tuned per line.
/// [variant] picks one of six typographic personalities.
class FitTitle extends StatelessWidget {
  const FitTitle(this.text, {super.key, required this.color, this.maxLines = 3, this.maxHeight, this.wght = 760, this.variant = 0});
  final String text;
  final Color color;
  final int maxLines, variant;
  final double? maxHeight;
  final double wght;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (ctx, c) {
        final w = c.maxWidth, grade = Theme.of(ctx).brightness == Brightness.dark ? -25.0 : 0.0;
        final lines = breakTitle(variant % 6 == 3 ? text.toUpperCase() : text, maxLines: maxLines);
        final base = w * (lines.length == 1 ? .26 : lines.length == 2 ? .19 : .15);
        final sp = [for (var i = 0; i < lines.length; i++) _line(variant, i, wght)];
        final f = [for (var i = 0; i < lines.length; i++) _fit(lines[i], w, base * sp[i].$1, sp[i].$2, sp[i].$3, sp[i].$4, grade, lines.length == 1)];
        return ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight ?? double.infinity),
          child: FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: w,
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                for (var i = 0; i < lines.length; i++)
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(lines[i], maxLines: 1, softWrap: false, style: sans(f[i].$1, wght: sp[i].$2, wdth: f[i].$2, rond: sp[i].$3, slnt: sp[i].$4, grade: grade, h: .98, ls: -f[i].$1 * .01, color: color)),
                  ),
              ]),
            ),
          ),
        );
      });
}
