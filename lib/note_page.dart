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

/// Reading view of a note. The title flexes out to full width when the page opens and shrinks into the
/// top-left corner as you scroll. One photo with one clear subject sits in an expressive shape and the subject
/// breaks out of it at the top; several photos become a carousel.
class NotePage extends StatefulWidget {
  const NotePage({super.key, required this.store, required this.note});
  final Store store;
  final Note note;
  @override
  State<NotePage> createState() => _NotePageState();
}

class _NotePageState extends State<NotePage> with SingleTickerProviderStateMixin {
  Color? seed;
  bool tint = true;
  final sc = ScrollController();
  late final AnimationController flexC = AnimationController(vsync: this, duration: const Duration(milliseconds: 950))..forward();
  late final Animation<double> flex = CurvedAnimation(parent: flexC, curve: Curves.easeOutCubic);
  Note get n => widget.note;
  List<Block> get photoBlocks => [for (final b in n.blocks) if (b.kind == Kind.photo && b.path != null) b];

  void loadSeed() => seedFromPhotos([for (final b in photoBlocks) b.path!]).then((c) {
        if (mounted) setState(() => seed = c);
      });

  @override
  void initState() {
    super.initState();
    seed = peekSeed([for (final b in photoBlocks) b.path!]);
    loadSeed();
    // cut out any photo that has not been cut yet (or was cut with an older version), then redraw
    for (final b in n.blocks) {
      if (wantsCut(b)) {
        ensureCut(b, widget.store).then((_) {
          if (mounted) setState(() {});
        });
      }
    }
  }

  @override
  void dispose() {
    sc.dispose();
    flexC.dispose();
    super.dispose();
  }

  Future<void> edit() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Editor(store: widget.store, note: n)));
    if (!mounted) return;
    if (!widget.store.notes.contains(n)) return Navigator.pop(context);
    setState(() {});
    loadSeed();
  }

  Widget tinted(Widget w, ColorScheme cs) => tint ? ColorFiltered(colorFilter: ColorFilter.mode(cs.primary.withAlpha(170), BlendMode.color), child: w) : w;

  @override
  Widget build(BuildContext context) {
    final cs = seed == null ? Theme.of(context).colorScheme : ColorScheme.fromSeed(seedColor: seed!, brightness: Theme.of(context).brightness);
    final bg = cs.primaryContainer, fg = cs.onPrimaryContainer, sw = MediaQuery.of(context).size.width, dark = Theme.of(context).brightness == Brightness.dark;
    final title = n.blocks.where((b) => !isTpl(b.kind) && b.kind != Kind.checklist).map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () => '');
    final texts = [for (final b in n.blocks) if (b.kind == Kind.text && b.body.isNotEmpty) b.body];
    final list = [for (final b in n.blocks) if (b.kind == Kind.checklist) ...b.items];
    final id = int.tryParse(n.id);
    final d = id == null ? null : DateTime.fromMicrosecondsSinceEpoch(id);
    const mon = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    TextStyle mono(double a, [bool bold = false]) => TextStyle(fontFamily: 'monospace', fontSize: 15, fontWeight: bold ? FontWeight.w700 : FontWeight.w400, color: fg.withValues(alpha: a));
    Widget info(String k, String v) => Expanded(child: Padding(padding: const EdgeInsets.only(bottom: 16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: mono(.7)), Text(v, style: mono(1, true))])));

    final photos = photoBlocks, w = sw - 32;
    // one photo with one clear subject pops out of a shape; several become a carousel
    final hero = photos.length == 1 && canPopOut(photos.first) ? photos.first : null;
    Widget photoSection() {
      if (hero != null) {
        final h = w * 1.2, s = subjectIn(hero, Size(w, h)) ?? Rect.zero;
        // the subject's top edge rides over the bottom of the title's last line, only slightly
        final lastLine = title.isEmpty ? 0.0 : titleLay(title, w, n.id, grade: dark ? -25.0 : 0.0).sizes.last;
        final lift = title.isEmpty ? s.top * .6 : (s.top + lastLine * .12).clamp(0.0, h * .5).toDouble();
        return SizedBox(
          height: h - lift,
          child: OverflowBox(
            alignment: Alignment.bottomCenter,
            minHeight: h,
            maxHeight: h,
            child: GestureDetector(onTap: () => setState(() => tint = !tint), child: PopOut(block: hero, fill: cs.primary, tint: tint ? cs.primary.withAlpha(150) : null, shape: seedOf(hero.id))),
          ),
        );
      }
      if (photos.length == 1) {
        final b = photos.first;
        return GestureDetector(
          onTap: () => setState(() => tint = !tint),
          child: ClipRRect(borderRadius: BorderRadius.circular(28), child: AspectRatio(aspectRatio: 1 / b.aspect.clamp(.6, 1.4), child: tinted(Image.file(File(b.path!), fit: BoxFit.cover, cacheWidth: 1200), cs))),
        );
      }
      return SizedBox(
        height: w * .7,
        child: CarouselView.weighted(
          flexWeights: const [8, 3, 1],
          itemSnapping: true,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
          onTap: (_) => setState(() => tint = !tint),
          children: [for (final b in photos) tinted(Image.file(File(b.path!), fit: BoxFit.cover, cacheWidth: 1200), cs)],
        ),
      );
    }

    // the small title in the bar: it fades in as the big one shrinks away
    final look = lookFor(n.id);
    Widget bar(double p) => Container(
          height: 56,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          color: bg.withAlpha((p * 255).round()),
          child: Row(children: [
            IconButton(icon: Icon(Icons.arrow_back, color: fg), onPressed: () => Navigator.pop(context)),
            Expanded(
              child: title.isEmpty
                  ? const SizedBox()
                  : Opacity(
                      opacity: p,
                      child: Transform.translate(
                        offset: Offset(0, (1 - p) * 16),
                        child: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: sans(22, color: fg, wght: look.wght.clamp(520, 800).toDouble(), rond: look.rond, slnt: look.slnt, ls: -.3)),
                      ),
                    ),
            ),
            for (final t in n.tags.take(1))
              Container(margin: const EdgeInsets.only(right: 8), padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8), decoration: BoxDecoration(color: cs.primary.withAlpha(50), borderRadius: BorderRadius.circular(20)), child: Text(t, style: TextStyle(color: fg))),
            GestureDetector(onTap: edit, child: CircleAvatar(backgroundColor: fg, child: Icon(Icons.edit_outlined, color: bg, size: 20))),
            const SizedBox(width: 4),
          ]),
        );

    return Scaffold(
      backgroundColor: bg,
      body: SafeArea(
        child: Stack(children: [
          ListView(controller: sc, padding: const EdgeInsets.fromLTRB(16, 64, 16, 40), children: [
            if (title.isNotEmpty)
              AnimatedBuilder(
                animation: Listenable.merge([sc, flex]),
                builder: (_, __) {
                  final p = sc.hasClients ? (sc.offset / 110).clamp(0.0, 1.0) : 0.0;
                  return Opacity(
                    opacity: 1 - p,
                    child: Transform.scale(scale: 1 - .6 * p, alignment: Alignment.topLeft, child: FitTitle(title, color: fg, seed: n.id, flex: flex.value)),
                  );
                },
              ),
            if (photos.isNotEmpty) ...[SizedBox(height: hero == null ? 20 : 0), photoSection()],
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
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AnimatedBuilder(animation: sc, builder: (_, __) => bar(sc.hasClients ? ((sc.offset - 40) / 70).clamp(0.0, 1.0) : 0.0)),
          ),
        ]),
      ),
    );
  }
}
