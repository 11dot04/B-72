import 'dart:math';
import 'package:flutter/material.dart';
import 'main.dart' show Kind, Lay, wide;
import 'type.dart';

bool isTpl(Kind k) => k == Kind.quote || k == Kind.stat || k == Kind.info || k == Kind.poster;

double tplWidth(Kind k) => switch (k) {
      Kind.bare || Kind.quote => .5,
      Kind.stat => .34,
      Kind.info => .46,
      Kind.link || Kind.recipe => .5,
      Kind.film || Kind.book => .4,
      Kind.track => .44,
      Kind.poster => .62,
      _ => .36,
    };

String tplLabel(Kind k) => switch (k) { Kind.quote => 'QUOTE', Kind.stat => 'FIGURE', Kind.info => 'DETAILS', Kind.link => 'LINK', _ => 'SMART' };

(Color, Color) tplColors(Kind k, ColorScheme cs) => switch (k) {
      Kind.quote => (cs.primary, cs.onPrimary),
      Kind.stat => (cs.tertiaryContainer, cs.onTertiaryContainer),
      Kind.info => (cs.secondaryContainer, cs.onSecondaryContainer),
      Kind.link => (cs.surface, cs.onSurface),
      _ => (cs.onPrimaryContainer, cs.primaryContainer),
    };

// ---------- smart layout: find the data in free text ----------

class Fact {
  Fact(this.label, this.value);
  final String label, value;
}

class Poster {
  Poster(this.head, this.big, this.facts, this.rest, this.rows);
  final String head, rest;
  final Fact? big; // the figure worth shouting about
  final List<Fact> facts;
  final List<(String, String)> rows; // ledger lines: label, amount
}

final _kv = RegExp(r'^\s*([A-Za-z][A-Za-z &/]{1,22}?)\s*[:=]\s*(.+?)\s*$');
final _url = RegExp(r'(?:https?://|www\.)\S+');
final _mail = RegExp(r'[\w.+-]+@[\w-]+\.[\w.]+');
const _mon = r'(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?';
final _date = RegExp(
    '\\b\\d{1,2}\\s?[–-]\\s?\\d{1,2}\\s$_mon(?:\\s\\d{4})?\\b|'
    '\\b\\d{1,2}(?:st|nd|rd|th)?\\s(?:of\\s)?$_mon(?:\\s\\d{4})?\\b|'
    '\\b$_mon\\s\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s\\d{4})?\\b|'
    '\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b|\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b|'
    '\\b\\d{1,2}(?:st|nd|rd|th)\\b',
    caseSensitive: false);
final _day = RegExp(r'\b(?:Mon|Tues?|Wed(?:nes)?|Thu(?:rs)?|Fri|Sat(?:ur)?|Sun)(?:day)?\b', caseSensitive: false);
final _time = RegExp(r'\b\d{1,2}(?::\d{2})?\s?(?:[–-]\s?\d{1,2}(?::\d{2})?\s?)?(?:am|pm)\b|\b\d{1,2}:\d{2}\b', caseSensitive: false);
final _money = RegExp(r'[$€£₹]\s?\d[\d,]*(?:\.\d+)?|\b\d[\d,]*(?:\.\d+)?\s?(?:USD|EUR|INR|GBP)\b');
final _pct = RegExp(r'\b\d+(?:\.\d+)?\s?%');
final _unit = RegExp(r'\b\d[\d,.]*\s?(?:km|kg|mins?|hrs?|hours?|ms|MB|GB|cm|kcal|reps|pages?|miles?|°C|°F)\b', caseSensitive: false);
final _phone = RegExp(r'\+?\d[\d\s().-]{8,}\d');
final _place = RegExp(r"(?:\b(?:at|in|to|near|from)|@)\s+((?:the\s+)?[A-Z][\w']+(?:\s+[A-Z][\w']+){0,3})");
const _notPlace = {'i', 'the', 'a', 'an', 'it', 'this', 'that', 'my', 'we', 'you', 'he', 'she', 'they', 'me', 'us', 'our', 'your'};

String _cap(String s) => s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);

String _unitLabel(String s) {
  final u = s.toLowerCase().replaceAll(RegExp(r'[\d,.\s]'), '');
  const m = {'km': 'Distance', 'mile': 'Distance', 'miles': 'Distance', 'kg': 'Weight', 'ms': 'Speed', 'mb': 'Size', 'gb': 'Size', 'cm': 'Length', 'kcal': 'Energy', 'reps': 'Reps', 'page': 'Pages', 'pages': 'Pages', '°c': 'Temp', '°f': 'Temp'};
  return m[u] ?? 'Duration';
}

String? _findPlace(String t) {
  for (final m in _place.allMatches(t)) {
    final p = m[1]!.trim(), first = p.split(RegExp(r'\s+')).first.toLowerCase();
    if (_notPlace.contains(first)) continue;
    if (RegExp('^$_mon\$', caseSensitive: false).hasMatch(first) || RegExp('^${_day.pattern}\$', caseSensitive: false).hasMatch(first)) continue;
    return p;
  }
  return null;
}

String _tidyHead(String s) {
  var h = s.replaceAll(RegExp(r'\s+'), ' ').replaceAll(RegExp(r'^[\s,.:;@–—-]+|[\s,.:;@–—-]+$'), '');
  final tail = RegExp(r'\s+(?:at|on|from|by|the|a|an|to|in|of|for|and|with|near)$', caseSensitive: false);
  while (tail.hasMatch(h)) {
    h = h.replaceFirst(tail, '');
  }
  return _cap(h.replaceAll(RegExp(r'[\s,.:;–—-]+$'), '').trim());
}

/// Reads free text and pulls out what a poster would show: headline, one big figure,
/// labelled facts (when, where, links, amounts), ledger rows, and whatever text is left.
Poster parsePoster(String title, String text) {
  final facts = <Fact>[], keep = <String>[];
  void add(String l, String v) {
    if (v.trim().isNotEmpty && !facts.any((f) => f.label == l)) facts.add(Fact(l, v.trim()));
  }

  // 1. "Label: value" lines (bullets and numbering stripped; never "https://...")
  for (final raw in text.split('\n')) {
    final line = raw.replaceFirst(RegExp(r'^\s*(?:[-•*>]+|\d+[.)])\s+'), '');
    final m = _kv.firstMatch(line);
    if (m != null && line.length < 90 && !m[2]!.startsWith('//')) {
      add(_cap(m[1]!.trim()), m[2]!);
    } else {
      keep.add(line);
    }
  }

  // 2. ledger: two or more short lines that each end in or contain one amount
  final rows = <(String, String)>[], other = <String>[];
  for (final l in keep) {
    final ms = _money.allMatches(l).toList();
    if (ms.length == 1 && l.trim().length < 60) {
      final lab = l.replaceFirst(ms.first[0]!, '').replaceAll(RegExp(r'\s+'), ' ').trim();
      if (lab.length >= 2) {
        rows.add((lab, ms.first[0]!.trim()));
        continue;
      }
    }
    other.add(l);
  }
  var t = (rows.length >= 2 ? other : keep).join('\n');
  if (rows.length < 2) rows.clear();

  String? take(RegExp r) {
    final m = r.firstMatch(t);
    if (m == null) return null;
    t = t.replaceFirst(m[0]!, ' ');
    return m[0]!.trim();
  }

  // 3. links, dates, times, amounts, figures
  final url = take(_url), mail = take(_mail);
  if (url != null) add('Link', url);
  if (mail != null) add('Email', mail);
  final date = take(_date), day = take(_day), time = take(_time);
  add('When', [day, date, time].whereType<String>().join(' · '));
  // several amounts in free text: the biggest one is the headline figure
  final amounts = _money.allMatches(t).toList();
  if (amounts.isNotEmpty) {
    double val(Match m) => double.tryParse(m[0]!.replaceAll(RegExp(r'[^\d.]'), '')) ?? 0;
    amounts.sort((a, b) => val(b).compareTo(val(a)));
    t = t.replaceFirst(amounts.first[0]!, ' ');
    add('Amount', amounts.first[0]!.trim());
  }
  final pct = take(_pct), unit = take(_unit);
  if (pct != null) add('Percent', pct);
  if (unit != null) add(_unitLabel(unit), unit);
  final ph = take(_phone);
  if (ph != null && RegExp(r'\d').allMatches(ph).length >= 9) add('Phone', ph);

  // 4. headline: the title, else the first short line / clause of what is left
  final lines = t.split('\n').map((l) => l.replaceAll(RegExp(r'\s+'), ' ').trim()).where((l) => l.isNotEmpty).toList();
  var head = title.trim();
  if (head.isEmpty && lines.isNotEmpty) {
    final first = lines.first, clause = RegExp(r'^(.{6,56}?)(?:[.!?:;—–]|,)(?:\s|$)').firstMatch(first);
    if (first.length <= 56) {
      head = lines.removeAt(0);
    } else if (clause != null) {
      head = clause[1]!;
      lines[0] = first.substring(clause[0]!.length).trim();
      if (lines[0].isEmpty) lines.removeAt(0);
    }
  }
  head = _tidyHead(head);

  // 5. place: shown as a fact unless the headline already says it
  final place = _findPlace('$head\n${lines.join('\n')}');
  if (place != null && !head.toLowerCase().contains(place.toLowerCase())) add('Where', place);

  Fact? big;
  for (final l in const ['Amount', 'Percent', 'Distance', 'Weight', 'Speed', 'Size', 'Energy', 'Reps', 'Pages', 'Temp', 'Duration']) {
    final i = facts.indexWhere((f) => f.label == l);
    if (i >= 0) {
      big = facts.removeAt(i);
      break;
    }
  }
  const order = ['Where', 'When', 'Link', 'Email', 'Phone'];
  facts.sort((a, b) {
    final x = order.indexOf(a.label), y = order.indexOf(b.label);
    return (x < 0 ? 99 : x).compareTo(y < 0 ? 99 : y);
  });
  return Poster(head, big, facts.take(6).toList(), lines.join('\n'), rows.take(8).toList());
}

List<(String, String)> pairs(String body) => [
      for (final l in body.split('\n'))
        if (l.trim().isNotEmpty)
          if (l.contains(':')) (l.substring(0, l.indexOf(':')).trim(), l.substring(l.indexOf(':') + 1).trim()) else ('', l.trim()),
    ];

String tplSummary(Kind k, String title, String body) {
  if (k == Kind.quote) return '“$body”${title.isEmpty ? '' : ' — $title'}';
  if (k == Kind.stat) return '$body${title.isEmpty ? '' : ' · $title'}';
  if (k == Kind.link) return '${title.isEmpty ? '' : '$title\n'}$body';
  if (k == Kind.poster) {
    final p = parsePoster(title, body);
    return [if (p.head.isNotEmpty) p.head, if (p.big != null) '${p.big!.label}: ${p.big!.value}', for (final f in p.facts.take(4)) '${f.label}: ${f.value}'].join(' · ');
  }
  return body.replaceAll('\n', ' · ');
}

// ---------- measuring and drawing (both use the same numbers) ----------

/// Regular weights are editorial (Instrument Serif); heavy ones are display (Google Sans Flex).
TextStyle _serif(double s, Color c, {FontWeight w = FontWeight.w400, double h = 1.15, FontStyle st = FontStyle.normal}) =>
    w.value >= 600 ? sans(s, color: c, wght: w.value.toDouble(), h: h) : serif(s, color: c, italic: st == FontStyle.italic, h: h);
TextStyle _caps(double s, Color c) => TextStyle(fontSize: s, fontWeight: FontWeight.w700, letterSpacing: .8, height: 1.25, color: c);

/// Zine headline: wide heavy sans with the last word in italic serif.
TextSpan _headSpan(String head, double s, Color c) {
  final base = sans(s, color: c, wght: 820, wdth: 118, h: 1.0, ls: -s * .02), i = head.lastIndexOf(' ');
  if (i < 0) return TextSpan(text: head, style: base);
  return TextSpan(style: base, children: [
    TextSpan(text: head.substring(0, i + 1)),
    TextSpan(text: head.substring(i + 1), style: serif(s * 1.1, color: c, italic: true, h: 1.0)),
  ]);
}

/// Height of a template card on the canvas (capped; long content is ellipsized).
Lay tplLay(Kind k, String title, String body, double wpx, double cw) {
  final u = cw / wide, o = u * .012, pad = u * .035, inner = wpx - 2 * o - 2 * pad, rowH = u * .1, frame = 2 * o + 2 * pad;
  double text(String s, TextStyle st, int? lines) => (TextPainter(text: TextSpan(text: s, style: st), textDirection: TextDirection.ltr, maxLines: lines)..layout(maxWidth: inner)).height;
  if (k == Kind.quote) {
    final line = u * .05 * 1.28, base = frame + u * .112 + (title.isEmpty ? 0.0 : u * .02 + u * .036 * 1.25);
    var h = base + text(body, _serif(u * .05, Colors.black, h: 1.28, st: FontStyle.italic), null), lines = 0;
    if (h > wpx) {
      h = max(wpx, base + line);
      lines = max(1, ((h - base) / line).floor());
    }
    return Lay(h, lines, 0, 0);
  }
  if (k == Kind.stat) return Lay(frame + u * .2, 0, 0, 0);
  if (k == Kind.link) return Lay(frame + u * .22, 0, 0, 0);
  if (k == Kind.info) {
    final n = pairs(body).length, base = frame + (title.isEmpty ? 0.0 : u * .07), fit = ((wpx - base) / rowH).floor();
    var show = n, hidden = 0;
    if (n > fit) {
      show = max(1, fit - 1);
      hidden = n - show;
    }
    return Lay(base + (show + (hidden > 0 ? 1 : 0)) * rowH, 0, show, hidden);
  }
  final p = parsePoster(title, body), nf = p.facts.length;
  final headH = p.head.isEmpty ? 0.0 : (TextPainter(text: _headSpan(p.head, u * .095, Colors.black), textDirection: TextDirection.ltr, maxLines: 3)..layout(maxWidth: inner)).height + u * .02;
  final h = frame +
      u * .065 + // running head
      headH +
      (p.big == null ? 0.0 : u * .03 + u * .04 + u * .2) +
      (nf == 0 ? 0.0 : u * .03 + ((nf + 1) ~/ 2) * u * .14) +
      (p.rows.isEmpty ? 0.0 : u * .03 + p.rows.length * u * .07) +
      (p.rest.isEmpty ? 0.0 : u * .03 + text(p.rest, _serif(u * .04, Colors.black, h: 1.3, st: FontStyle.italic), 4)) +
      u * .07; // footer
  return Lay(min(h, wpx * 1.5), 0, 0, 0);
}

/// The content of a template. [lay] is null on the full-size note page (show everything).
Widget tplBody(Kind k, String title, String body, double u, Color fg, ColorScheme cs, Lay? lay) {
  final rowH = u * .1;
  Widget cell(String label, String value) => SizedBox(
        height: rowH,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (label.isNotEmpty) Text(label.toUpperCase(), maxLines: 1, overflow: TextOverflow.ellipsis, style: _caps(u * .028, fg.withAlpha(150))),
          Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: u * .04, height: 1.25, fontWeight: FontWeight.w600, color: fg)),
        ]),
      );
  if (k == Kind.quote) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('“', style: _serif(u * .16, fg.withAlpha(150), h: .7)),
      Text(body, maxLines: (lay?.lines ?? 0) == 0 ? null : lay!.lines, overflow: TextOverflow.ellipsis, style: _serif(u * .05, fg, h: 1.28, st: FontStyle.italic)),
      if (title.isNotEmpty) Padding(padding: EdgeInsets.only(top: u * .02), child: Text('— $title', style: TextStyle(fontSize: u * .036, height: 1.25, color: fg.withAlpha(200)))),
    ]);
  }
  if (k == Kind.stat) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title.toUpperCase(), maxLines: 1, overflow: TextOverflow.ellipsis, style: _caps(u * .032, fg.withAlpha(170))),
      SizedBox(height: u * .01),
      FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerLeft, child: Text(body, style: _serif(u * .13, fg, w: FontWeight.w700, h: 1.0))),
    ]);
  }
  if (k == Kind.link) {
    final host = Uri.tryParse(body.startsWith('http') ? body : 'https://$body')?.host ?? body;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('LINK', style: _caps(u * .03, fg.withAlpha(150))),
      SizedBox(height: u * .01),
      Text(title.isEmpty ? host : title, maxLines: 2, overflow: TextOverflow.ellipsis, style: _serif(u * .05, fg)),
      SizedBox(height: u * .01),
      Text(body, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'monospace', fontSize: u * .032, color: fg.withAlpha(190))),
    ]);
  }
  if (k == Kind.info) {
    final ps = pairs(body), show = lay?.rows ?? ps.length;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (title.isNotEmpty) SizedBox(height: u * .07, child: Align(alignment: Alignment.centerLeft, child: Text(title, maxLines: 1, style: _serif(u * .05, fg)))),
      for (final p in ps.take(show)) cell(p.$1, p.$2),
      if ((lay?.hidden ?? 0) > 0) SizedBox(height: rowH, child: Align(alignment: Alignment.centerLeft, child: Text('+${lay!.hidden} more', style: _caps(u * .03, fg.withAlpha(170))))),
    ]);
  }

  // ---- smart layout as a zine page: running head, big headline, one shouted figure, indexed facts, ledger, pull text
  final p = parsePoster(title, body), hair = fg.withAlpha(80), zh = u * .14;
  Widget micro(String s) => Text(s.toUpperCase(), maxLines: 1, overflow: TextOverflow.ellipsis, style: _caps(u * .026, fg.withAlpha(180)));
  Widget zcell(Fact f, int n) => Container(
        height: zh,
        margin: EdgeInsets.only(right: u * .02),
        padding: EdgeInsets.only(top: u * .012),
        decoration: BoxDecoration(border: Border(top: BorderSide(color: hair))),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${(n + 1).toString().padLeft(2, '0')} · ${f.label.toUpperCase()}', maxLines: 1, overflow: TextOverflow.ellipsis, style: _caps(u * .024, fg.withAlpha(150))),
          Text(f.value, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: u * .04, height: 1.2, fontWeight: FontWeight.w600, color: fg)),
        ]),
      );
  final no = '№ ${(title.length * 7 + body.length) % 90 + 10}', nFacts = p.facts.length + (p.big == null ? 0 : 1);
  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Container(
      height: u * .045,
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: hair))),
      child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [micro(no), const Spacer(), micro(p.facts.isNotEmpty ? p.facts.first.label : 'Note')]),
    ),
    SizedBox(height: u * .02),
    if (p.head.isNotEmpty) ...[
      Text.rich(_headSpan(p.head, u * .095, fg), maxLines: 3, overflow: TextOverflow.ellipsis),
      SizedBox(height: u * .02),
    ],
    if (p.big != null) ...[
      SizedBox(height: u * .03),
      micro(p.big!.label),
      SizedBox(height: u * .2, child: FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerLeft, child: Text(p.big!.value, style: _serif(u * .17, cs.tertiaryContainer, w: FontWeight.w900, h: 1.0)))),
    ],
    if (p.facts.isNotEmpty) ...[
      SizedBox(height: u * .03),
      for (var i = 0; i < p.facts.length; i += 2)
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(child: zcell(p.facts[i], i)),
          Expanded(child: i + 1 < p.facts.length ? zcell(p.facts[i + 1], i + 1) : const SizedBox()),
        ]),
    ],
    if (p.rows.isNotEmpty) ...[
      SizedBox(height: u * .03),
      for (final r in p.rows)
        Container(
          height: u * .07,
          decoration: BoxDecoration(border: Border(bottom: BorderSide(color: hair))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
            Expanded(child: Text(r.$1, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'monospace', fontSize: u * .03, color: fg.withAlpha(210)))),
            Text(r.$2, style: TextStyle(fontSize: u * .038, fontWeight: FontWeight.w700, color: fg)),
          ]),
        ),
    ],
    if (p.rest.isNotEmpty) ...[
      SizedBox(height: u * .03),
      Text(p.rest, maxLines: 4, overflow: TextOverflow.ellipsis, style: _serif(u * .04, fg.withAlpha(220), h: 1.3, st: FontStyle.italic)),
    ],
    SizedBox(height: u * .03),
    SizedBox(height: u * .04, child: Row(children: [micro('B-72'), const Spacer(), micro('$nFacts ${nFacts == 1 ? 'fact' : 'facts'}')])),
  ]);
}

/// Full-width card for the note page.
Widget tplCard(Kind k, String title, String body, double u, ColorScheme cs) {
  final (bg, fg) = tplColors(k, cs);
  return Container(
    width: double.infinity,
    padding: EdgeInsets.all(u * .05),
    decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(u * .07), border: k == Kind.link ? Border.all(color: fg.withAlpha(60)) : null),
    child: tplBody(k, title, body, u, fg, cs, null),
  );
}
