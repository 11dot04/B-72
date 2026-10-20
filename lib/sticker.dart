import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:google_mlkit_subject_segmentation/google_mlkit_subject_segmentation.dart';

class Sticker {
  Sticker(this.id, this.path);
  final String id, path;
  Map toJson() => {'i': id, 'p': path};
  factory Sticker.fromJson(Map j) => Sticker(j['i'], j['p']);
}

/// Approximate distance (px) from every pixel to the nearest foreground pixel (two-pass chamfer).
Float32List _distance(Uint8List fg, int w, int h) {
  final d = Float32List(w * h);
  for (var i = 0; i < d.length; i++) {
    d[i] = fg[i] == 1 ? 0 : 1e9;
  }
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      final i = y * w + x;
      var v = d[i];
      if (x > 0) v = math.min(v, d[i - 1] + 1);
      if (y > 0) {
        v = math.min(v, d[i - w] + 1);
        if (x > 0) v = math.min(v, d[i - w - 1] + 1.414);
        if (x < w - 1) v = math.min(v, d[i - w + 1] + 1.414);
      }
      d[i] = v;
    }
  }
  for (var y = h - 1; y >= 0; y--) {
    for (var x = w - 1; x >= 0; x--) {
      final i = y * w + x;
      var v = d[i];
      if (x < w - 1) v = math.min(v, d[i + 1] + 1);
      if (y < h - 1) {
        v = math.min(v, d[i + w] + 1);
        if (x < w - 1) v = math.min(v, d[i + w + 1] + 1.414);
        if (x > 0) v = math.min(v, d[i + w - 1] + 1.414);
      }
      d[i] = v;
    }
  }
  return d;
}

/// Cuts every recognised subject (objects, pets, people) out of a photo and
/// returns one die-cut sticker PNG per subject: the cut-out plus a white outline.
Future<List<String>> makeStickers(String src, Directory dir) async {
  // Work on an upright, downscaled copy so mask coordinates are unambiguous.
  final codec = await ui.instantiateImageCodec(await File(src).readAsBytes(), targetWidth: 1024);
  final img = (await codec.getNextFrame()).image;
  final w = img.width, h = img.height;
  final rgba = (await img.toByteData(format: ui.ImageByteFormat.rawRgba))!;
  final png = (await img.toByteData(format: ui.ImageByteFormat.png))!;
  final tmp = File('${dir.path}/_seg.png')..writeAsBytesSync(png.buffer.asUint8List());

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

  final out = <String>[];
  for (final s in res.subjects) {
    final m = s.confidenceMask;
    if (m == null || m.length != s.width * s.height || s.width < 24 || s.height < 24) continue;
    final r = math.max(5, (math.min(s.width, s.height) * .045).round()), pad = r + 2;
    final sw = s.width + 2 * pad, sh = s.height + 2 * pad;
    final fg = Uint8List(sw * sh);
    for (var y = 0; y < s.height; y++) {
      for (var x = 0; x < s.width; x++) {
        if (m[y * s.width + x] > .5) fg[(y + pad) * sw + x + pad] = 1;
      }
    }
    final dist = _distance(fg, sw, sh);
    final px = Uint8List(sw * sh * 4);
    for (var y = 0; y < sh; y++) {
      for (var x = 0; x < sw; x++) {
        final i = y * sw + x, o = i * 4, d = dist[i];
        if (d > r + .5) continue;
        if (fg[i] == 1) {
          final sx = x - pad, sy = y - pad, gx = s.startX + sx, gy = s.startY + sy;
          if (gx < 0 || gy < 0 || gx >= w || gy >= h) continue;
          final mv = ((m[sy * s.width + sx] - .3) / .4).clamp(0.0, 1.0), ix = (gy * w + gx) * 4;
          for (var c = 0; c < 3; c++) {
            px[o + c] = (255 + (rgba.getUint8(ix + c) - 255) * mv).round();
          }
          px[o + 3] = 255;
        } else {
          px[o] = px[o + 1] = px[o + 2] = 255;
          px[o + 3] = ((r + .5 - d).clamp(0.0, 1.0) * 255).round();
        }
      }
    }
    final done = Completer<ui.Image>();
    ui.decodeImageFromPixels(px, sw, sh, ui.PixelFormat.rgba8888, done.complete);
    final bytes = (await (await done.future).toByteData(format: ui.ImageByteFormat.png))!;
    final f = File('${dir.path}/s_${DateTime.now().microsecondsSinceEpoch}_${out.length}.png')..writeAsBytesSync(bytes.buffer.asUint8List());
    out.add(f.path);
  }
  return out;
}
