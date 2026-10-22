import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart' show Alignment, Rect, Size;
import 'package:google_mlkit_commons/google_mlkit_commons.dart';
import 'package:google_mlkit_subject_segmentation/google_mlkit_subject_segmentation.dart';
import 'main.dart' show Kind;
import 'store.dart';

/// Photos and recipe images get a subject cut-out so titles can sit behind the subject
/// and the note page can let it break out of a shape.
const _ver = 2; // bump to re-cut existing photos

bool wantsCut(Block b) => b.path != null && (b.kind == Kind.photo || b.kind == Kind.recipe);

/// Path of the transparent cut-out, or null if there is none (yet, or no clear subject).
String? cutPath(Block b) {
  final p = b.data['cut'];
  return p is String && p.isNotEmpty && File(p).existsSync() ? p : null;
}

/// Makes the cut-out once per block. Stores '' when the photo has no clear subject so we don't retry.
/// Errors (e.g. the ML Kit model still downloading) leave the block untouched so it is retried later.
Future<void> ensureCut(Block b, Store s) async {
  if (!wantsCut(b) || !File(b.path!).existsSync()) return;
  final c = b.data['cut'];
  if (b.data['cutV'] == _ver && c is String && (c.isEmpty || File(c).existsSync())) return;
  try {
    final r = await _cut(b.path!, s.dir);
    if (c is String && c.isNotEmpty) {
      try {
        File(c).deleteSync();
      } catch (_) {}
    }
    if (r == null) {
      b.data['cut'] = '';
    } else {
      b.data['cut'] = r.$1;
      b.data['cutBox'] = r.$2;
      b.data['cutAspect'] = r.$3;
      b.data['cutFrac'] = r.$4;
    }
    b.data['cutV'] = _ver;
    s.save();
  } catch (_) {}
}

/// How much of the frame the subject covers (0..1), or 0 when there is no cut-out.
double cutFrac(Block b) => cutPath(b) == null ? 0 : ((b.data['cutFrac'] as num?)?.toDouble() ?? .3);

/// Where the subject lands inside a tile that shows the photo with BoxFit.cover and [al] alignment.
Rect? cutRectIn(Block b, Size tile, Alignment al) {
  final box = b.data['cutBox'], asp = b.data['cutAspect'];
  if (box is! List || box.length < 4 || asp is! num) return null;
  final ar = asp.toDouble();
  final scale = math.max(tile.width / ar, tile.height);
  final dw = ar * scale, dh = scale;
  final ox = (tile.width - dw) * (al.x + 1) / 2, oy = (tile.height - dh) * (al.y + 1) / 2;
  double d(int i) => (box[i] as num).toDouble();
  return Rect.fromLTRB(ox + d(0) * dw, oy + d(1) * dh, ox + d(2) * dw, oy + d(3) * dh);
}

/// (png path, subject box 0..1 as [l, t, r, b], image aspect w/h), or null when there is no clear subject.
Future<(String, List<double>, double, double)?> _cut(String src, Directory dir) async {
  // Upright, downscaled copy so mask coordinates are unambiguous (same trick as the stickers).
  final codec = await ui.instantiateImageCodec(await File(src).readAsBytes(), targetWidth: 1024);
  final img = (await codec.getNextFrame()).image;
  final w = img.width, h = img.height;
  final rgba = (await img.toByteData(format: ui.ImageByteFormat.rawRgba))!;
  final png = (await img.toByteData(format: ui.ImageByteFormat.png))!;
  final tmp = File('${dir.path}/_cut_${DateTime.now().microsecondsSinceEpoch}.png')..writeAsBytesSync(png.buffer.asUint8List());

  final seg = SubjectSegmenter(
    options: SubjectSegmenterOptions(
      enableForegroundConfidenceMask: false,
      enableForegroundBitmap: false,
      enableMultipleSubjects: SubjectResultOptions(enableConfidenceMask: true, enableSubjectBitmap: false),
    ),
  );
  final SubjectSegmentationResult res;
  try {
    res = await seg.processImage(InputImage.fromFilePath(tmp.path));
  } finally {
    seg.close();
    if (tmp.existsSync()) tmp.deleteSync();
  }

  // Union of every subject's confidence mask, in full-image coordinates.
  final a = Float32List(w * h);
  for (final s in res.subjects) {
    final m = s.confidenceMask;
    if (m == null || m.length != s.width * s.height) continue;
    for (var y = 0; y < s.height; y++) {
      final gy = s.startY + y;
      if (gy < 0 || gy >= h) continue;
      for (var x = 0; x < s.width; x++) {
        final gx = s.startX + x;
        if (gx < 0 || gx >= w) continue;
        final v = m[y * s.width + x], i = gy * w + gx;
        if (v > a[i]) a[i] = v;
      }
    }
  }

  var n = 0, l = w, t = h, r = 0, bt = 0;
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      if (a[y * w + x] > .5) {
        n++;
        l = math.min(l, x);
        r = math.max(r, x);
        t = math.min(t, y);
        bt = math.max(bt, y);
      }
    }
  }
  final frac = n / (w * h);
  if (frac < .05 || frac > .8) return null; // nothing clear, or the subject is the whole frame

  // Soften the rim so the subject doesn't sit outlined over the photo: pull the mask in by a pixel
  // (drops the background-coloured fringe), then feather it with a small blur.
  final e = Float32List(w * h);
  for (var y = 1; y < h - 1; y++) {
    for (var x = 1; x < w - 1; x++) {
      final i = y * w + x;
      e[i] = math.min(math.min(a[i], a[i - 1]), math.min(math.min(a[i + 1], a[i - w]), a[i + w]));
    }
  }
  final blur = Float32List(w * h);
  for (var y = 0; y < h; y++) {
    for (var x = 1; x < w - 1; x++) {
      final i = y * w + x;
      blur[i] = (e[i - 1] + 2 * e[i] + e[i + 1]) / 4;
    }
  }
  for (var y = 1; y < h - 1; y++) {
    for (var x = 0; x < w; x++) {
      final i = y * w + x;
      a[i] = (blur[i - w] + 2 * blur[i] + blur[i + w]) / 4;
    }
  }

  final px = Uint8List(w * h * 4);
  for (var i = 0; i < w * h; i++) {
    final o = i * 4, al = ((a[i] - .25) / .6).clamp(0.0, 1.0);
    px[o] = rgba.getUint8(o);
    px[o + 1] = rgba.getUint8(o + 1);
    px[o + 2] = rgba.getUint8(o + 2);
    px[o + 3] = (al * 255).round();
  }
  final done = Completer<ui.Image>();
  ui.decodeImageFromPixels(px, w, h, ui.PixelFormat.rgba8888, done.complete);
  final bytes = (await (await done.future).toByteData(format: ui.ImageByteFormat.png))!;
  final f = File('${dir.path}/cut_${DateTime.now().microsecondsSinceEpoch}.png')..writeAsBytesSync(bytes.buffer.asUint8List());
  return (f.path, [l / w, t / h, (r + 1) / w, (bt + 1) / h], w / h, frac);
}
