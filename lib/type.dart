import 'dart:math';
import 'package:flutter/material.dart';

const kSans = 'GoogleSansFlex', kSerif = 'InstrumentSerif';

/// Stable hash (String.hashCode is not guaranteed stable across runs).
int seedOf(String s) {
  var h = 7;
  for (final c in s.codeUnits) {
    h = (h * 31 + c) & 0x7fffffff;
  }
  return h;
}

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

/// How the last word of a title is set apart. Only one look uses the serif italic; the rest differ.
enum Accent { none, serif, flip, marker, outline }

/// One personality for a title. Each note picks one from its id, so a grid of
/// titles reads like a magazine rack instead of one repeated style.
class TitleLook {
  const TitleLook(this.wght, this.rond, this.slnt, {this.upper = false, this.accent = Accent.none});
  final double wght, rond, slnt;
  final bool upper; // ALL CAPS
  final Accent accent;
}

const _looks = [
  TitleLook(800, 100, 0), // soft heavy
  TitleLook(900, 0, 0, upper: true), // hard poster caps
  TitleLook(300, 0, 0, accent: Accent.flip), // light, last word heavy
  TitleLook(720, 100, -10), // slanted, round
  TitleLook(560, 45, 0, accent: Accent.marker), // highlighter swipe on the last word
  TitleLook(880, 0, 0, accent: Accent.serif), // the one serif-italic look
  TitleLook(420, 100, 0, upper: true, accent: Accent.outline), // rounded caps, outlined last word
  TitleLook(650, 0, -8), // slanted, sharp
];

TitleLook lookFor(String seed) => _looks[seedOf(seed) % _looks.length];

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

/// One title line in a given look. The measuring and the drawing both use this.
TextSpan _mk(String line, double s, double wd, TitleLook k, double grade, Color? c, bool accent) {
  final t = k.upper ? line.toUpperCase() : line;
  final base = sans(s, color: c, wght: k.wght, wdth: wd, grade: grade, rond: k.rond, slnt: k.slnt, h: .98, ls: -s * .01);
  final i = t.lastIndexOf(' ');
  if (!accent || i < 0 || k.accent == Accent.none) return TextSpan(text: t, style: base);
  final TextStyle last = switch (k.accent) {
    Accent.serif => serif(s * 1.1, color: c, italic: true, h: .98),
    Accent.flip => sans(s, color: c, wght: k.wght < 500 ? 900 : 200, wdth: wd, grade: grade, rond: k.rond, slnt: k.slnt, h: .98, ls: -s * .01),
    Accent.marker => base.copyWith(backgroundColor: (c ?? Colors.black).withAlpha(55)),
    _ => sans(s, wght: k.wght, wdth: wd, grade: grade, rond: k.rond, slnt: k.slnt, h: .98, ls: -s * .01).copyWith(foreground: Paint()..style = PaintingStyle.stroke..strokeWidth = max(1.2, s * .02)..color = c ?? Colors.black),
  };
  return TextSpan(style: base, children: [TextSpan(text: t.substring(0, i + 1)), TextSpan(text: t.substring(i + 1), style: last)]);
}

/// Picks the width axis (and, if needed, the size) so a line fills [w] exactly.
(double, double) _fit(TextSpan Function(double s, double wd) mk, double w, double size, bool grow) {
  double width(double s, double wd) => (TextPainter(text: mk(s, wd), textDirection: TextDirection.ltr)..layout()).width;
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

/// The measured title: balanced lines, each with the size and width axis that fill [w].
class TitleLay {
  TitleLay(this.look, this.lines, this.sizes, this.wdths, this.height, this.grade);
  final TitleLook look;
  final List<String> lines;
  final List<double> sizes, wdths;
  final double height, grade;
  bool accentAt(int i) => look.accent != Accent.none && i == lines.length - 1 && lines[i].contains(' ');
  TextSpan span(int i, Color c, [double flex = 1]) => _mk(lines[i], sizes[i], 25 + (wdths[i] - 25) * flex, look, grade, c, accentAt(i));
}

TitleLay titleLay(String text, double w, String seed, {int maxLines = 3, double grade = 0}) {
  final k = lookFor(seed.isEmpty ? text : seed);
  final lines = breakTitle(k.upper ? text.toUpperCase() : text, maxLines: maxLines);
  final size = w * (lines.length == 1 ? .26 : lines.length == 2 ? .19 : .15);
  final sz = <double>[], wd = <double>[];
  var h = 0.0;
  for (var i = 0; i < lines.length; i++) {
    final acc = k.accent != Accent.none && i == lines.length - 1 && lines[i].contains(' ');
    final f = _fit((s, x) => _mk(lines[i], s, x, k, grade, null, acc), w, size, lines.length == 1);
    sz.add(f.$1);
    wd.add(f.$2);
    h += (TextPainter(text: _mk(lines[i], f.$1, f.$2, k, grade, null, acc), textDirection: TextDirection.ltr)..layout()).height;
  }
  return TitleLay(k, lines, sz, wd, h, grade);
}

/// A title that always fills its width. [flex] (0..1) animates the width axis from condensed to full.
class FitTitle extends StatelessWidget {
  const FitTitle(this.text, {super.key, required this.color, this.maxLines = 3, this.maxHeight, this.seed = '', this.flex = 1});
  final String text;
  final Color color;
  final int maxLines;
  final double? maxHeight;
  final String seed;
  final double flex;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (ctx, c) {
        final w = c.maxWidth, grade = Theme.of(ctx).brightness == Brightness.dark ? -25.0 : 0.0;
        final l = titleLay(text, w, seed, maxLines: maxLines, grade: grade);
        return ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight ?? double.infinity),
          child: FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: w,
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                for (var i = 0; i < l.lines.length; i++)
                  FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerLeft, child: Text.rich(l.span(i, color, flex), maxLines: 1, softWrap: false)),
              ]),
            ),
          ),
        );
      });
}
