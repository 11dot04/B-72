import 'dart:io';
import 'package:flutter/material.dart';
import 'cutout.dart';
import 'editor.dart';
import 'main.dart' show Kind;
import 'palette.dart';
import 'popout.dart';
import 'records.dart';
import 'templates.dart';
import 'type.dart';
import 'store.dart';

/// Reading view of a note: poster-style header that fills the width, tinted photos, info rows.
/// A photo with a clear subject sits in an expressive shape and the subject breaks out of it.
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
    // cut out any photo that has not been cut yet, then redraw
    for (final b in n.blocks) {
      if (wantsCut(b) && b.data['cut'] == null) ensureCut(b, widget.store).then((_) {
          if (mounted) setState(() {});
        });
    }
  }

  Future<void> edit() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Editor(store: widget.store, note: n)));
    if (!mounted) return;
    if (!widget.store.notes.contains(n)) return Navigator.pop(context);
    setState(() {});
    loadSeed();
  }

  @override
  Widget build(BuildContext context) {
    final cs = seed == null ? Theme.of(context).colorScheme : ColorScheme.fromSeed(seedColor: seed!, brightness: Theme.of(context).brightness);
    final bg = cs.primaryContainer, fg = cs.onPrimaryContainer, sw = MediaQuery.of(context).size.width;
    final title = n.blocks.where((b) => !isTpl(b.kind) && b.kind != Kind.checklist).map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () => '');
    final texts = [for (final b in n.blocks) if (b.kind == Kind.text && b.body.isNotEmpty) b.body];
    final list = [for (final b in n.blocks) if (b.kind == Kind.checklist) ...b.items];
    final id = int.tryParse(n.id);
    final d = id == null ? null : DateTime.fromMicrosecondsSinceEpoch(id);
    const mon = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    TextStyle mono(double a, [bool bold = false]) => TextStyle(fontFamily: 'monospace', fontSize: 15, fontWeight: bold ? FontWeight.w700 : FontWeight.w400, color: fg.withValues(alpha: a));
    Widget info(String k, String v) => Expanded(child: Padding(padding: const EdgeInsets.only(bottom: 16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: mono(.7)), Text(v, style: mono(1, true))])));

    // the first photo with a clear subject becomes the hero; the rest stay in the carousel
    final heroB = [for (final b in n.blocks) if (b.kind == Kind.photo && cutPath(b) != null) b].firstOrNull;
    final carousel = [for (final b in n.blocks) if (b.kind == Kind.photo && b.path != null && !identical(b, heroB)) b.path!];
    final heroW = sw - 32, heroH = heroW * 1.15, lift = heroW * .1;

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
          if (title.isNotEmpty) FitTitle(title, color: fg, seed: n.id),
          if (heroB != null) ...[
            // lifted so the subject's top overlaps the title; the title sits behind it
            SizedBox(
              height: heroH - lift,
              child: OverflowBox(
                alignment: Alignment.bottomCenter,
                minHeight: heroH,
                maxHeight: heroH,
                child: GestureDetector(
                  onTap: () => setState(() => tint = !tint),
                  child: PopOut(photo: heroB.path!, cut: cutPath(heroB)!, fill: cs.primary, tint: tint ? cs.primary.withAlpha(150) : null, shape: seedOf(heroB.id)),
                ),
              ),
            ),
          ],
          if (carousel.isNotEmpty) ...[
            const SizedBox(height: 20),
            SizedBox(
              height: (sw - 32) * .7,
              child: CarouselView.weighted(
                flexWeights: const [8, 3, 1],
                itemSnapping: true,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                onTap: (_) => setState(() => tint = !tint),
                children: [
                  for (final p in carousel)
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
            if (isTpl(b.kind)) Padding(padding: const EdgeInsets.only(bottom: 14), child: tplCard(b.kind, b.title, b.body, sw * 1.1, cs)),
          for (final b in n.blocks)
            if (isRec(b.kind)) recSection(context, b.kind, b, cs, sw, title != b.title),
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
