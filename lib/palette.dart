import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:material_color_utilities/material_color_utilities.dart';

final _cache = <String, Color?>{};

Color? peekSeed(List<String> paths) => _cache[paths.join('|')];

/// Dominant colour across a set of photos, using the same quantize + score
/// pipeline Material You uses for wallpapers. Cached per photo set.
Future<Color?> seedFromPhotos(List<String> paths) async {
  if (paths.isEmpty) return null;
  final key = paths.join('|');
  if (_cache.containsKey(key)) return _cache[key];
  try {
    final px = <int>[];
    for (final p in paths) {
      final codec = await ui.instantiateImageCodec(await File(p).readAsBytes(), targetWidth: 64);
      final img = (await codec.getNextFrame()).image;
      final d = (await img.toByteData(format: ui.ImageByteFormat.rawRgba))!;
      for (var i = 0; i < d.lengthInBytes; i += 4) {
        if (d.getUint8(i + 3) < 128) continue;
        px.add((0xFF << 24) | (d.getUint8(i) << 16) | (d.getUint8(i + 1) << 8) | d.getUint8(i + 2));
      }
    }
    final q = await QuantizerCelebi().quantize(px, 128);
    return _cache[key] = Color(Score.score(q.colorToCount, desired: 1).first);
  } catch (_) {
    return _cache[key] = null;
  }
}
