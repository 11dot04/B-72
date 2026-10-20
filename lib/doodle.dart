import 'dart:math';
import 'package:flutter/material.dart';
import 'main.dart' show NoteCard;

/// Hand-drawn connectors between the cards of one note. Painted underneath
/// the cards, so a doodle can never cover content.
class Doodles extends CustomPainter {
  Doodles(this.groups, this.cw, this.cs);
  final Map<String, List<NoteCard>> groups;
  final double cw;
  final ColorScheme cs;

  Offset edge(Rect r, Offset t) {
    final d = t - r.center;
    final k = min(r.width / 2 / (d.dx.abs() + 1e-6), r.height / 2 / (d.dy.abs() + 1e-6));
    return r.center + d * k;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final cols = [cs.primary, cs.tertiary, cs.secondary];
    groups.forEach((id, list) {
      if (list.length < 2) return;
      final s = [...list]..sort((a, b) => a.order.compareTo(b.order));
      final paint = Paint()
        ..color = cols[id.hashCode.abs() % 3]
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3.5
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round;
      for (var i = 0; i < s.length - 1; i++) {
        final a = s[i].rect(cw), b = s[i + 1].rect(cw);
        if (a.overlaps(b)) continue;
        final p0 = edge(a, b.center), p1 = edge(b, a.center), v = p1 - p0, len = v.distance;
        if (len < 12) continue;
        final dir = v / len, nrm = Offset(-dir.dy, dir.dx), waves = max(2, (len / 55).round());
        Offset at(double t) => p0 + v * t + nrm * (sin(pi * t) * len * .1 + sin(t * 2 * pi * waves) * 5 * sin(pi * t));
        final path = Path()..moveTo(p0.dx, p0.dy);
        for (var k = 1; k <= 48; k++) {
          final p = at(k / 48);
          path.lineTo(p.dx, p.dy);
        }
        canvas.drawPath(path, paint);
        final from = at(.95), h = atan2(p1.dy - from.dy, p1.dx - from.dx);
        for (final da in [2.6, -2.6]) {
          canvas.drawLine(p1, p1 + Offset(cos(h + da), sin(h + da)) * 13, paint);
        }
      }
    });
  }

  @override
  bool shouldRepaint(Doodles old) => true;
}
