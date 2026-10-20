import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart' show Rect;
import 'package:path_provider/path_provider.dart';
import 'main.dart' show Kind, NoteCard;

class Block {
  Block(this.kind, {this.title = '', this.body = '', this.path, this.focal = const Rect.fromLTRB(.3, .3, .7, .7), this.aspect = 1});
  Kind kind;
  String title, body;
  String? path;
  Rect focal;
  double aspect;

  Map toJson() => {'k': kind.index, 't': title, 'b': body, 'p': path, 'f': [focal.left, focal.top, focal.right, focal.bottom], 'a': aspect};

  factory Block.fromJson(Map j) {
    final f = (j['f'] as List).map((e) => (e as num).toDouble()).toList();
    return Block(Kind.values[j['k']],
        title: j['t'], body: j['b'], path: j['p'], focal: Rect.fromLTRB(f[0], f[1], f[2], f[3]), aspect: (j['a'] as num).toDouble());
  }
}

class Place {
  Place(this.x, this.y);
  double x, y;
}

/// A note is an ordered list of blocks. Its position on each tag's canvas
/// is stored per tag, so the same note can sit differently on different canvases.
class Note {
  Note(this.id, {List<Block>? blocks, Set<String>? tags, Map<String, Place>? places})
      : blocks = blocks ?? [],
        tags = tags ?? {},
        places = places ?? {};
  final String id;
  List<Block> blocks;
  Set<String> tags;
  Map<String, Place> places;

  /// The block a canvas card shows: first photo, else first block.
  Block? get cover => blocks.isEmpty ? null : blocks.firstWhere((b) => b.kind == Kind.photo, orElse: () => blocks.first);

  Map toJson() => {
        'id': id,
        'blocks': blocks.map((b) => b.toJson()).toList(),
        'tags': tags.toList(),
        'places': places.map((k, v) => MapEntry(k, [v.x, v.y])),
      };

  factory Note.fromJson(Map j) => Note(
        j['id'],
        blocks: (j['blocks'] as List).map((b) => Block.fromJson(b)).toList(),
        tags: (j['tags'] as List).map((t) => t as String).toSet(),
        places: (j['places'] as Map).map((k, v) => MapEntry(k as String, Place((v[0] as num).toDouble(), (v[1] as num).toDouble()))),
      );
}

/// Simple JSON-file storage. Swap for SQLite later if it outgrows this.
class Store {
  Store(this.file, this.dir, this.notes);
  final File file;
  final Directory dir;
  final List<Note> notes;

  static Future<Store> load() async {
    final d = await getApplicationDocumentsDirectory();
    final dir = Directory('${d.path}/photos')..createSync(recursive: true);
    final f = File('${d.path}/notes.json');
    var notes = <Note>[];
    try {
      if (f.existsSync()) notes = (jsonDecode(f.readAsStringSync()) as List).map((j) => Note.fromJson(j)).toList();
    } catch (_) {}
    return Store(f, dir, notes);
  }

  void save() => file.writeAsStringSync(jsonEncode(notes.map((n) => n.toJson()).toList()));

  Set<String> get tags => {for (final n in notes) ...n.tags};
  int count(String tag) => notes.where((n) => n.tags.contains(tag)).length;

  Future<String> keepPhoto(String src) async => (await File(src).copy('${dir.path}/${DateTime.now().microsecondsSinceEpoch}.jpg')).path;

  void setPlace(String tag, String id, double x, double y) => notes.firstWhere((n) => n.id == id).places[tag] = Place(x, y);

  /// Canvas cards for one tag. Unplaced notes start at the top and get
  /// moved to a free spot by the canvas's tidy pass.
  List<NoteCard> cardsFor(String tag) => [
        for (final n in notes)
          if (n.tags.contains(tag) && n.cover != null) cardOf(n, tag),
      ];

  NoteCard cardOf(Note n, String tag) {
    final b = n.cover!, p = n.places[tag], photo = b.kind == Kind.photo;
    return NoteCard(n.id, b.kind, p?.x ?? .3, p?.y ?? 0, photo ? .38 : (b.kind == Kind.bare ? .5 : .36), photo ? b.aspect : 0,
        angle: photo ? ((n.id.hashCode % 9) - 4) * .01 : 0,
        focal: b.focal,
        path: b.path,
        title: b.title,
        titleSize: b.kind == Kind.bare ? .07 : .05,
        body: b.body);
  }
}
