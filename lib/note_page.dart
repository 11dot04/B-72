import 'dart:io';
import 'package:flutter/material.dart';
import 'editor.dart';
import 'main.dart' show Kind;
import 'palette.dart';
import 'templates.dart';
import 'type.dart';
import 'store.dart';

/// Reading view of a note: poster-style header that fills the width, tinted photos, info rows.
class NotePage extends StatefulWidget {
  const NotePage({super.key, required this.store, required this.note});
  final Store store;
  final Note note;
  @override
  State<NotePage> createState() => _NotePageState();
}

class _NotePageState extends State<NotePage> {
  Color? seed;
  bool tint = true;
  final pc = PageController(viewportFraction: .9);
  Note get n => widget.note;
  List<String> get photos => [for (final b in n.blocks) if (b.kind == Kind.photo && b.path != null) b.path!];

  void loadSeed() => seedFromPhotos(photos).then((c) {
        if (mounted) setState(() => seed = c);
      });

  @override
  void initState() {
    super.initState();
    seed = peekSeed(photos);
    loadSeed();
  }

  Future<void> edit() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Editor(store: widget.store, note: n)));
    if (!mounted) return;
    if (!widget.store.notes.contains(n)) return Navigator.pop(context);
    setState(() {});
    loadSeed();
  }

  /// Greedy line breaks so every line can be scaled to the full width.
  List<String> lines(String t) {
    final out = <String>[];
    var cur = '';
    for (final w in t.toUpperCase().split(RegExp(r'\s+'))) {
      if (cur.isNotEmpty && cur.length + w.length + 1 > 10) {
        out.add(cur);
        cur = w;
      } else {
        cur = cur.isEmpty ? w : '$cur $w';
      }
    }
    return cur.isEmpty ? out : [...out, cur];
  }

  @override
  Widget build(BuildContext context) {
    final cs = seed == null ? Theme.of(context).colorScheme : ColorScheme.fromSeed(seedColor: seed!, brightness: Theme.of(context).brightness);
    final bg = cs.primaryContainer, fg = cs.onPrimaryContainer;
    final title = n.blocks.where((b) => !isTpl(b.kind)).map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () {
      final b = n.blocks.firstWhere((b) => b.body.isNotEmpty, orElse: () => n.blocks.first);
      return b.title.isNotEmpty ? b.title : (b.body.isEmpty ? 'Note' : b.body.split(' ').take(3).join(' '));
    });
    final texts = [for (final b in n.blocks) if (b.kind == Kind.text && b.body.isNotEmpty) b.body];
    final list = [for (final b in n.blocks) if (b.kind == Kind.checklist) ...b.items];
    final id = int.tryParse(n.id);
    final d = id == null ? null : DateTime.fromMicrosecondsSinceEpoch(id);
    const mon = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    TextStyle mono(double a, [bool bold = false]) => TextStyle(fontFamily: 'monospace', fontSize: 15, fontWeight: bold ? FontWeight.w700 : FontWeight.w400, color: fg.withValues(alpha: a));
    Widget info(String k, String v) => Expanded(child: Padding(padding: const EdgeInsets.only(bottom: 16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: mono(.7)), Text(v, style: mono(1, true))])));
    return Scaffold(
      backgroundColor: bg,
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 40), children: [
          Row(children: [
            IconButton(icon: Icon(Icons.arrow_back, color: fg), onPressed: () => Navigator.pop(context)),
            const Spacer(),
            for (final t in n.tags.take(2))
              Container(margin: const EdgeInsets.only(right: 8), padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8), decoration: BoxDecoration(color: cs.primary.withAlpha(50), borderRadius: BorderRadius.circular(20)), child: Text(t, style: TextStyle(color: fg))),
            GestureDetector(onTap: edit, child: CircleAvatar(backgroundColor: fg, child: Icon(Icons.edit_outlined, color: bg, size: 20))),
          ]),
          const SizedBox(height: 20),
          FitTitle(title, color: fg, wght: 800),
          if (photos.isNotEmpty) ...[
            const SizedBox(height: 20),
            SizedBox(
              height: 380,
              child: CarouselView.weighted(
                flexWeights: const [7, 3, 1],
                itemSnapping: true,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                onTap: (_) => setState(() => tint = !tint),
                children: [
                  for (final p in photos)
                    ColorFiltered(
                      colorFilter: ColorFilter.mode(tint ? cs.primary.withAlpha(170) : Colors.transparent, BlendMode.color),
                      child: Image.file(File(p), fit: BoxFit.cover, cacheWidth: 1200),
                    ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 24),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            info('Tags', n.tags.isEmpty ? 'None' : n.tags.join(', ')),
            info('Created', d == null ? '-' : '${d.day} ${mon[d.month - 1]} ${d.year}'),
          ]),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            info('Photos', '${photos.length}'),
            info('Checklist', list.isEmpty ? '-' : '${list.where((s) => s.startsWith('x ')).length} of ${list.length} done'),
          ]),
          for (final b in n.blocks)
            if (isTpl(b.kind)) Padding(padding: const EdgeInsets.only(bottom: 14), child: tplCard(b.kind, b.title, b.body, MediaQuery.of(context).size.width * 1.1, cs)),
          for (final t in texts) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(t, style: TextStyle(fontSize: 18, height: 1.45, color: fg))),
          for (final s in list)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(children: [
                Icon(s.startsWith('x ') ? Icons.radio_button_checked : Icons.radio_button_unchecked, color: fg, size: 22),
                const SizedBox(width: 12),
                Expanded(child: Text(s.startsWith('x ') ? s.substring(2) : s, style: TextStyle(fontSize: 17, color: fg, decoration: s.startsWith('x ') ? TextDecoration.lineThrough : null))),
              ]),
            ),
        ]),
      ),
    );
  }
}
