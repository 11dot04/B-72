import 'dart:io';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'cutout.dart';
import 'main.dart' show Kind;
import 'popout.dart';
import 'store.dart';
import 'type.dart';
import 'web.dart';

/// Templates that hold structured data and can be filled from the web.
bool isRec(Kind k) => k == Kind.film || k == Kind.track || k == Kind.recipe || k == Kind.link || k == Kind.book;

const recMeta = {
  Kind.film: ('Film', Icons.movie_outlined),
  Kind.track: ('Track', Icons.music_note_outlined),
  Kind.recipe: ('Recipe', Icons.restaurant_menu),
  Kind.link: ('Link', Icons.link),
  Kind.book: ('Book', Icons.menu_book_outlined),
};

/// Canvas card height / width. Music is always a square; films and books are portrait rectangles (2:3).
double recAspect(Kind k) => switch (k) { Kind.film => 1.5, Kind.book => 1.5, Kind.track => 1.0, Kind.recipe => 1.15, _ => .85 };

String _v(Map d, String k) => '${d[k] ?? ''}'.trim();
bool _has(String? p) => p != null && File(p).existsSync();

String recSub(Kind k, Map d) {
  final parts = switch (k) {
    Kind.film => [_v(d, 'year'), _v(d, 'director')],
    Kind.track => [_v(d, 'artist'), _v(d, 'album')],
    Kind.book => [_v(d, 'author'), _v(d, 'year')],
    Kind.recipe => [if (_v(d, 'time').isNotEmpty && _v(d, 'time') != '0') '${_v(d, 'time')} min', if (_v(d, 'servings').isNotEmpty) 'serves ${_v(d, 'servings')}'],
    _ => [_v(d, 'site')],
  };
  return parts.where((e) => e.isNotEmpty).join(' · ');
}

Widget _stars(int r, Color c, double size) => Row(mainAxisSize: MainAxisSize.min, children: [
      for (var i = 1; i <= 5; i++) Icon(i <= r ? Icons.star_rounded : Icons.star_outline_rounded, size: size, color: c),
    ]);

Future<void> fillLink(Block b, Store s) async {
  final p = await linkPreview(b.body);
  if (p.isEmpty) return;
  if (b.title.isEmpty) b.title = '${p['title'] ?? ''}';
  b.data['desc'] = p['desc'];
  b.data['site'] = p['site'];
  final path = await saveImage(p['image'] as String?, s.dir);
  if (path != null) b.path = path;
}

// ---------- canvas card ----------

Widget recCard(Kind k, String title, Map d, String? path, double u, ColorScheme cs) {
  final has = _has(path), sub = recSub(k, d), rating = (d['rating'] as num?)?.toInt() ?? 0;
  final fg = has ? Colors.white : cs.onPrimaryContainer;
  return Stack(fit: StackFit.expand, children: [
    ColoredBox(color: has ? Colors.black : cs.primaryContainer),
    if (has) Image.file(File(path!), fit: BoxFit.cover, cacheWidth: 700),
    if (has)
      DecoratedBox(
        decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black.withAlpha(200)], stops: const [.42, 1])),
      ),
    Positioned(
      left: u * .04,
      right: u * .04,
      bottom: u * .035,
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title.isEmpty ? recMeta[k]!.$1 : title, maxLines: 2, overflow: TextOverflow.ellipsis, style: sans(u * .06, wght: 720, color: fg, h: 1.05)),
        if (sub.isNotEmpty) Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: u * .035, color: fg.withAlpha(215))),
      ]),
    ),
    Positioned(
      top: u * .035,
      left: u * .035,
      child: Container(padding: EdgeInsets.all(u * .02), decoration: const BoxDecoration(color: Colors.black38, shape: BoxShape.circle), child: Icon(recMeta[k]!.$2, size: u * .045, color: Colors.white)),
    ),
    if (rating > 0)
      Positioned(
        top: u * .035,
        right: u * .035,
        child: Container(padding: EdgeInsets.symmetric(horizontal: u * .02, vertical: u * .008), decoration: BoxDecoration(color: Colors.black45, borderRadius: BorderRadius.circular(u * .03)), child: _stars(rating, Colors.amber, u * .04)),
      ),
  ]);
}

// ---------- note page section ----------

Widget recSection(BuildContext ctx, Kind k, Block b, ColorScheme cs, double u, bool showTitle) {
  final fg = cs.onPrimaryContainer, d = b.data, p = b.path, has = _has(p);
  String v(String x) => _v(d, x);
  Widget img(double aspect, {double r = 28}) => ClipRRect(borderRadius: BorderRadius.circular(r), child: AspectRatio(aspectRatio: aspect, child: Image.file(File(p!), fit: BoxFit.cover, cacheWidth: 1000)));
  Widget chip(IconData i, String t) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(color: fg.withAlpha(28), borderRadius: BorderRadius.circular(20)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(i, size: 16, color: fg), const SizedBox(width: 6), Text(t, style: TextStyle(color: fg, fontWeight: FontWeight.w600))]),
      );
  Widget label(String t) => Padding(padding: const EdgeInsets.only(top: 20, bottom: 8), child: Text(t, style: TextStyle(fontFamily: 'monospace', fontSize: 13, color: fg.withAlpha(170))));
  Widget title(String t) => showTitle && t.isNotEmpty ? Padding(padding: const EdgeInsets.only(top: 14), child: Text(t, style: sans(30, wght: 760, color: fg, h: 1.05))) : const SizedBox.shrink();
  Widget notes(String l, String text) => text.isEmpty ? const SizedBox.shrink() : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [label(l), Text(text, style: TextStyle(fontSize: 18, height: 1.45, color: fg))]);
  Widget body;
  if (k == Kind.film || k == Kind.book) {
    final isBook = k == Kind.book, r = (d['rating'] as num?)?.toInt() ?? 0;
    final meta = isBook
        ? [v('year'), if (v('pages').isNotEmpty && v('pages') != '0') '${v('pages')} pages', v('genre')]
        : [v('year'), if (v('runtime').isNotEmpty) '${v('runtime')} min', v('genre')];
    final by = isBook ? (v('author').isEmpty ? '' : 'By ${v('author')}') : (v('director').isEmpty ? '' : 'Directed by ${v('director')}');
    body = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (has) SizedBox(width: u * .38, child: img(2 / 3, r: 22)),
        if (has) const SizedBox(width: 16),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (showTitle) Text(b.title, style: sans(26, wght: 760, color: fg, h: 1.05)),
            const SizedBox(height: 8),
            Text(meta.where((e) => e.isNotEmpty).join(' · '), style: TextStyle(fontFamily: 'monospace', fontSize: 14, color: fg.withAlpha(200))),
            if (by.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text(by, style: TextStyle(fontSize: 15, color: fg))),
            if (r > 0) Padding(padding: const EdgeInsets.only(top: 12), child: _stars(r, fg, 26)),
          ]),
        ),
      ]),
      if (!isBook && v('overview').isNotEmpty) Padding(padding: const EdgeInsets.only(top: 16), child: Text(v('overview'), maxLines: 6, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 15, height: 1.4, color: fg.withAlpha(200)))),
      notes('YOUR THOUGHTS', b.body),
    ]);
  } else if (k == Kind.track) {
    body = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (has) Align(alignment: Alignment.centerLeft, child: SizedBox(width: u * .8, child: img(1))),
      title(b.title),
      if (v('artist').isNotEmpty) Padding(padding: const EdgeInsets.only(top: 4), child: Text(v('artist'), style: sans(20, wght: 500, color: fg.withAlpha(220)))),
      Padding(
        padding: const EdgeInsets.only(top: 12),
        child: Wrap(spacing: 8, runSpacing: 8, children: [
          if (v('album').isNotEmpty) chip(Icons.album_outlined, v('album')),
          if (v('year').isNotEmpty) chip(Icons.calendar_today_outlined, v('year')),
          if (v('genre').isNotEmpty) chip(Icons.graphic_eq, v('genre')),
        ]),
      ),
      notes('WHY YOU LOVE IT', b.body),
    ]);
  } else if (k == Kind.recipe) {
    final ing = v('ingredients').split('\n').where((e) => e.trim().isNotEmpty).toList(), st = v('steps').split('\n').where((e) => e.trim().isNotEmpty).toList();
    final cut = cutPath(b);
    body = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      // a dish with a clear subject breaks out of an expressive shape; otherwise a plain photo
      if (has && cut != null)
        SizedBox(height: u * 1.0, child: PopOut(photo: p!, cut: cut, fill: cs.primary, tint: cs.primary.withAlpha(120), shape: seedOf(b.id)))
      else if (has)
        img(4 / 3),
      title(b.title),
      Padding(
        padding: const EdgeInsets.only(top: 12),
        child: Wrap(spacing: 8, runSpacing: 8, children: [
          if (v('time').isNotEmpty && v('time') != '0') chip(Icons.timer_outlined, '${v('time')} min'),
          if (v('servings').isNotEmpty) chip(Icons.people_outline, 'Serves ${v('servings')}'),
        ]),
      ),
      if (ing.isNotEmpty) label('INGREDIENTS'),
      for (final e in ing)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: const EdgeInsets.only(top: 8, right: 12), child: Icon(Icons.circle, size: 6, color: fg)),
            Expanded(child: Text(e, style: TextStyle(fontSize: 17, height: 1.35, color: fg))),
          ]),
        ),
      if (st.isNotEmpty) label('STEPS'),
      for (var i = 0; i < st.length; i++)
        Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SizedBox(width: 46, child: Text('${i + 1}', style: sans(32, wght: 800, color: fg.withAlpha(110), h: 1))),
            Expanded(child: Text(st[i], style: TextStyle(fontSize: 17, height: 1.4, color: fg))),
          ]),
        ),
      notes('NOTES', b.body),
    ]);
  } else {
    final url = b.body.startsWith('http') ? b.body : 'https://${b.body}';
    body = Container(
      decoration: BoxDecoration(color: fg.withAlpha(24), borderRadius: BorderRadius.circular(28)),
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (has) AspectRatio(aspectRatio: 16 / 9, child: Image.file(File(p!), fit: BoxFit.cover, width: double.infinity, cacheWidth: 1000)),
        Padding(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (v('site').isNotEmpty) Text(v('site').toUpperCase(), style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: .8, color: fg.withAlpha(170))),
            if (b.title.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text(b.title, style: sans(24, wght: 720, color: fg, h: 1.1))),
            if (v('desc').isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(v('desc'), maxLines: 4, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 15, height: 1.4, color: fg.withAlpha(210)))),
            Padding(padding: const EdgeInsets.only(top: 10), child: Text(b.body, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'monospace', fontSize: 12, color: fg.withAlpha(150)))),
            const SizedBox(height: 14),
            FilledButton.tonalIcon(onPressed: () => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication), icon: const Icon(Icons.open_in_new, size: 18), label: const Text('OPEN')),
            if (v('note').isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(v('note'), style: TextStyle(fontSize: 16, height: 1.4, color: fg))),
          ]),
        ),
      ]),
    );
  }
  return Padding(padding: const EdgeInsets.only(bottom: 26), child: body);
}

// ---------- editor form ----------

const _fields = {
  Kind.film: [('year', 'Year'), ('director', 'Director'), ('genre', 'Genre'), ('runtime', 'Runtime (min)')],
  Kind.track: [('artist', 'Artist'), ('album', 'Album'), ('year', 'Year'), ('genre', 'Genre')],
  Kind.book: [('author', 'Author'), ('year', 'Year'), ('genre', 'Genre'), ('pages', 'Pages')],
  Kind.recipe: [('time', 'Time (min)'), ('servings', 'Serves')],
};

class RecordForm extends StatefulWidget {
  const RecordForm({super.key, required this.block, required this.store, required this.onRemove});
  final Block block;
  final Store store;
  final VoidCallback onRemove;
  @override
  State<RecordForm> createState() => _RecordFormState();
}

class _RecordFormState extends State<RecordForm> {
  late final Block b = widget.block;
  late final Kind k = b.kind;
  final ctrl = <String, TextEditingController>{};
  bool busy = false;

  String read(String key) => key == 'title' ? b.title : key == 'body' ? b.body : '${b.data[key] ?? ''}';
  void write(String key, String v) {
    if (key == 'title') {
      b.title = v;
    } else if (key == 'body') {
      b.body = v;
    } else {
      b.data[key] = v;
    }
  }

  void set(String key, String v) {
    write(key, v);
    ctrl[key]?.text = v;
  }

  void snack(String t) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(t)));

  Future<void> search() async {
    final q = (ctrl['title']?.text ?? b.title).trim();
    if (q.isEmpty) return;
    setState(() => busy = true);
    final res = switch (k) { Kind.film => await searchFilms(q), Kind.book => await searchBooks(q), _ => await searchTracks(q) };
    if (!mounted) return;
    setState(() => busy = false);
    if (res.isEmpty) return snack("Nothing found. Check the title and your connection.");
    final pick = await showModalBottomSheet<Cand>(
      context: context,
      builder: (_) => ListView(children: [
        for (final r in res)
          ListTile(
            leading: r.image == null || r.image!.isEmpty
                ? null
                : ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.network(r.image!, width: 44, height: 44, fit: BoxFit.cover, cacheWidth: 120, errorBuilder: (_, __, ___) => const SizedBox(width: 44, height: 44))),
            title: Text(r.title),
            subtitle: Text(r.sub),
            onTap: () => Navigator.pop(context, r),
          ),
      ]),
    );
    if (pick == null || !mounted) return;
    set('title', pick.title);
    pick.data.forEach((key, v) {
      if ('$v'.isNotEmpty) set(key, '$v');
    });
    setState(() => busy = true);
    final path = await saveImage(pick.image, widget.store.dir);
    if (path != null) b.path = path;
    if (mounted) setState(() => busy = false);
  }

  Future<void> importIt() async {
    final url = (ctrl['url']?.text ?? '').trim();
    if (url.isEmpty) return;
    setState(() => busy = true);
    final r = await importRecipe(url);
    if (!mounted) return;
    if (r.isEmpty) {
      setState(() => busy = false);
      return snack("Couldn't find a recipe on that page. You can fill it in by hand.");
    }
    for (final key in ['title', 'ingredients', 'steps', 'time', 'servings', 'url']) {
      if ('${r[key] ?? ''}'.isNotEmpty) set(key, '${r[key]}');
    }
    final path = await saveImage(r['image'] as String?, widget.store.dir);
    if (path != null) {
      b.path = path;
      b.data.remove('cut');
      ensureCut(b, widget.store); // cut the dish out in the background so the title can sit behind it
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> fetchIt() async {
    final url = (ctrl['body']?.text ?? b.body).trim();
    if (url.isEmpty) return;
    b.body = url;
    setState(() => busy = true);
    await fillLink(b, widget.store);
    if (!mounted) return;
    set('title', b.title);
    setState(() => busy = false);
    if (b.data['desc'] == null && b.title.isEmpty) snack("Couldn't read a preview. The link will still be saved.");
  }

  @override
  Widget build(BuildContext context) {
    final meta = recMeta[k]!, rating = (b.data['rating'] as num?)?.toInt() ?? 0;
    Widget spinner(IconData i, VoidCallback f) => busy ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))) : IconButton(icon: Icon(i), onPressed: f);
    Widget field(String key, String label, {int lines = 1, Widget? suffix}) => Padding(
          padding: const EdgeInsets.only(top: 8),
          child: TextField(
            controller: ctrl.putIfAbsent(key, () => TextEditingController(text: read(key))),
            minLines: lines,
            maxLines: lines == 1 ? 1 : 8,
            decoration: InputDecoration(labelText: label, isDense: true, suffixIcon: suffix),
            onChanged: (v) => write(key, v),
          ),
        );
    final pairs = _fields[k] ?? const <(String, String)>[];
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(meta.$2, size: 18),
            const SizedBox(width: 8),
            Text(meta.$1.toUpperCase(), style: Theme.of(context).textTheme.labelSmall),
            const Spacer(),
            IconButton(icon: const Icon(Icons.close, size: 18), onPressed: widget.onRemove),
          ]),
          if (_has(b.path) && k != Kind.link) Padding(padding: const EdgeInsets.only(bottom: 4), child: ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(b.path!), height: 110, fit: BoxFit.cover, cacheWidth: 400))),
          if (k == Kind.film || k == Kind.track || k == Kind.book) field('title', k == Kind.film ? 'Film title' : k == Kind.book ? 'Book title' : 'Song title', suffix: spinner(Icons.search, search)),
          if (k == Kind.recipe) ...[
            field('url', 'Recipe link (optional)', suffix: spinner(Icons.download, importIt)),
            field('title', 'Recipe name'),
          ],
          if (k == Kind.link) ...[
            field('body', 'Link', suffix: spinner(Icons.travel_explore, fetchIt)),
            field('title', 'Title'),
          ],
          for (var i = 0; i < pairs.length; i += 2)
            Row(children: [
              Expanded(child: field(pairs[i].$1, pairs[i].$2)),
              const SizedBox(width: 10),
              Expanded(child: i + 1 < pairs.length ? field(pairs[i + 1].$1, pairs[i + 1].$2) : const SizedBox()),
            ]),
          if (k == Kind.film || k == Kind.book)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Row(children: [
                const Text('Your rating  '),
                for (var i = 1; i <= 5; i++)
                  GestureDetector(onTap: () => setState(() => b.data['rating'] = rating == i ? 0 : i), child: Icon(i <= rating ? Icons.star_rounded : Icons.star_outline_rounded, size: 30)),
              ]),
            ),
          if (k == Kind.recipe) ...[
            field('ingredients', 'Ingredients (one per line)', lines: 4),
            field('steps', 'Steps (one per line)', lines: 4),
          ],
          field(k == Kind.link ? 'note' : 'body', k == Kind.film || k == Kind.book ? 'Your thoughts' : k == Kind.track ? 'Why you love it' : 'Notes', lines: 2),
        ]),
      ),
    );
  }
}
