import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

const _ua = {
  'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36',
  'Accept-Language': 'en',
};

Future<http.Response?> _get(String url) async {
  try {
    return await http.get(Uri.parse(url), headers: _ua).timeout(const Duration(seconds: 14));
  } catch (_) {
    return null;
  }
}

Future<String?> fetchText(String url) async {
  final r = await _get(url);
  return r != null && r.statusCode == 200 ? utf8.decode(r.bodyBytes, allowMalformed: true) : null;
}

/// Downloads an image into the app's storage and returns its path.
Future<String?> saveImage(String? url, Directory dir) async {
  if (url == null || url.isEmpty) return null;
  final r = await _get(url);
  if (r == null || r.statusCode != 200 || r.bodyBytes.length < 800) return null;
  return (File('${dir.path}/w_${DateTime.now().microsecondsSinceEpoch}.jpg')..writeAsBytesSync(r.bodyBytes)).path;
}

String _clean(String s) => s
    .replaceAll(RegExp(r'<[^>]+>'), ' ')
    .replaceAll('&amp;', '&')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&#x27;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&nbsp;', ' ')
    .replaceAll(RegExp(r'\s+'), ' ')
    .trim();

/// A search result the user can pick from.
class Cand {
  Cand(this.title, this.sub, this.image, this.data);
  final String title, sub;
  final String? image;
  final Map<String, dynamic> data;
}

String _year(dynamic d) => d == null ? '' : '$d'.split('-').first;
String _art(dynamic u, int n) => u == null ? '' : '$u'.replaceAll(RegExp(r'\d+x\d+bb'), '${n}x${n}bb');

// The iTunes Search API needs no key.
Future<List<dynamic>> _itunes(String q, String entity) async {
  final t = await fetchText('https://itunes.apple.com/search?term=${Uri.encodeQueryComponent(q)}&entity=$entity&limit=6');
  if (t == null) return [];
  try {
    return jsonDecode(t)['results'] as List;
  } catch (_) {
    return [];
  }
}

Future<List<Cand>> _itunesFilms(String q) async => [
      for (final r in await _itunes(q, 'movie'))
        if (r['trackName'] != null)
          Cand('${r['trackName']}', '${_year(r['releaseDate'])} · ${r['artistName'] ?? ''}', _art(r['artworkUrl100'], 600), {
            'year': _year(r['releaseDate']),
            'director': '${r['artistName'] ?? ''}',
            'genre': '${r['primaryGenreName'] ?? ''}',
            'runtime': r['trackTimeMillis'] is num ? '${((r['trackTimeMillis'] as num) / 60000).round()}' : '',
            'overview': '${r['longDescription'] ?? r['shortDescription'] ?? ''}',
          }),
    ];

Future<List<Cand>> searchTracks(String q) async => [
      for (final r in await _itunes(q, 'song'))
        if (r['trackName'] != null)
          Cand('${r['trackName']}', '${r['artistName'] ?? ''} · ${r['collectionName'] ?? ''}', _art(r['artworkUrl100'], 600), {
            'artist': '${r['artistName'] ?? ''}',
            'album': '${r['collectionName'] ?? ''}',
            'year': _year(r['releaseDate']),
            'genre': '${r['primaryGenreName'] ?? ''}',
          }),
    ];

/// Open Graph preview of a web page.
Future<Map<String, dynamic>> linkPreview(String url) async {
  final html = await fetchText(url);
  if (html == null) return {};
  String? meta(String p) {
    for (final re in [
      RegExp('<meta[^>]+(?:property|name)=["\']$p["\'][^>]*content=["\']([^"\']*)["\']', caseSensitive: false),
      RegExp('<meta[^>]+content=["\']([^"\']*)["\'][^>]*(?:property|name)=["\']$p["\']', caseSensitive: false),
    ]) {
      final m = re.firstMatch(html);
      if (m != null && m[1]!.trim().isNotEmpty) return _clean(m[1]!);
    }
    return null;
  }

  final host = (Uri.tryParse(url)?.host ?? '').replaceFirst('www.', '');
  final title = meta('og:title') ?? meta('twitter:title') ?? RegExp(r'<title[^>]*>([^<]*)</title>', caseSensitive: false).firstMatch(html)?[1];
  final img = meta('og:image') ?? meta('twitter:image');
  return {
    'title': title == null ? '' : _clean(title),
    'desc': meta('og:description') ?? meta('description') ?? '',
    'site': meta('og:site_name') ?? host,
    'image': img == null ? null : Uri.parse(url).resolve(img).toString(),
  };
}

/// Reads a recipe page's schema.org data (most recipe sites publish it).
Future<Map<String, dynamic>> importRecipe(String url) async {
  final html = await fetchText(url);
  if (html == null) return {};
  Map? find(dynamic j) {
    if (j is List) {
      for (final e in j) {
        final r = find(e);
        if (r != null) return r;
      }
    } else if (j is Map) {
      final t = j['@type'];
      if (t == 'Recipe' || (t is List && t.contains('Recipe'))) return j;
      for (final k in ['@graph', 'mainEntity']) {
        if (j[k] != null) {
          final r = find(j[k]);
          if (r != null) return r;
        }
      }
    }
    return null;
  }

  Map? rec;
  for (final m in RegExp(r'<script[^>]+ld\+json[^>]*>(.*?)</script>', caseSensitive: false, dotAll: true).allMatches(html)) {
    try {
      rec = find(jsonDecode(m[1]!));
    } catch (_) {}
    if (rec != null) break;
  }
  if (rec == null) return {};
  String? img(dynamic i) => i is String ? i : i is List && i.isNotEmpty ? img(i.first) : i is Map ? '${i['url']}' : null;
  List<String> steps(dynamic s) {
    final out = <String>[];
    if (s is String) {
      out.addAll(s.split(RegExp(r'\n+')));
    } else if (s is List) {
      for (final e in s) {
        if (e is Map && e['itemListElement'] != null) {
          out.addAll(steps(e['itemListElement']));
        } else if (e is Map) {
          out.add('${e['text'] ?? e['name'] ?? ''}');
        } else {
          out.add('$e');
        }
      }
    }
    return [for (final e in out) _clean(e)].where((e) => e.isNotEmpty).toList();
  }

  int mins(dynamic d) {
    final m = RegExp(r'P(?:\d+D)?T?(?:(\d+)H)?(?:(\d+)M)?').firstMatch('$d');
    return m == null ? 0 : int.parse(m[1] ?? '0') * 60 + int.parse(m[2] ?? '0');
  }

  final y = rec['recipeYield'], ing = rec['recipeIngredient'], im = img(rec['image']);
  return {
    'title': _clean('${rec['name'] ?? ''}'),
    'image': im == null ? null : Uri.parse(url).resolve(im).toString(),
    'ingredients': ing is List ? ing.map((e) => _clean('$e')).where((e) => e.isNotEmpty).join('\n') : '',
    'steps': steps(rec['recipeInstructions']).join('\n'),
    'time': '${mins(rec['totalTime'] ?? rec['cookTime'] ?? rec['prepTime'])}',
    'servings': RegExp(r'\d+').firstMatch('${y is List && y.isNotEmpty ? y.first : y ?? ''}')?[0] ?? '',
    'url': url,
  };
}

/// Films come from Wikipedia (no key, big catalogue); iTunes is the fallback.
Future<List<Cand>> searchFilms(String q) async {
  final t = await fetchText(
      'https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${Uri.encodeQueryComponent('$q film')}&gsrlimit=8&prop=pageimages%7Cextracts&piprop=thumbnail&pithumbsize=600&pilicense=any&exintro=1&explaintext=1&exchars=800&redirects=1');
  final out = <Cand>[];
  try {
    final pages = ((jsonDecode(t ?? '{}')['query']?['pages'] as Map?) ?? {}).values.toList()
      ..sort((a, b) => ((a['index'] ?? 0) as num).compareTo((b['index'] ?? 0) as num));
    for (final p in pages) {
      final ex = '${p['extract'] ?? ''}';
      if (!RegExp(r'\bfilm\b', caseSensitive: false).hasMatch(ex)) continue;
      final year = RegExp(r'\b(1[89]\d\d|20\d\d)\b').firstMatch(ex)?[1] ?? '';
      final dir = RegExp(r"directed by ([A-Z][^,.;()]*?)(?= and | starring| from | with | based|,|\.|;|\(|$)").firstMatch(ex)?[1]?.trim() ?? '';
      final genre = RegExp(r'(?:is|was) an? (?:\d{4} )?(.+?) film').firstMatch(ex)?[1]?.trim() ?? '';
      out.add(Cand('${p['title']}'.replaceAll(RegExp(r'\s*\([^)]*film\)'), ''), [year, dir].where((e) => e.isNotEmpty).join(' · '), p['thumbnail']?['source'] as String?, {
        'year': year,
        'director': dir,
        'genre': genre,
        'runtime': '',
        'overview': ex.split(RegExp(r'(?<=\.)\s+')).take(2).join(' '),
      }));
    }
  } catch (_) {}
  return out.isNotEmpty ? out : await _itunesFilms(q);
}

/// Books come from Google Books (no key), with Open Library as the fallback.
Future<List<Cand>> searchBooks(String q) async {
  final out = <Cand>[];
  final t = await fetchText('https://www.googleapis.com/books/v1/volumes?q=${Uri.encodeQueryComponent(q)}&maxResults=8&printType=books');
  try {
    for (final it in (jsonDecode(t ?? '{}')['items'] as List? ?? [])) {
      final v = (it['volumeInfo'] as Map?) ?? {};
      if (v['title'] == null) continue;
      final links = (v['imageLinks'] as Map?) ?? {};
      final img = '${links['thumbnail'] ?? links['smallThumbnail'] ?? ''}'.replaceAll('http://', 'https://').replaceAll('&edge=curl', '').replaceAll('zoom=1', 'zoom=2');
      final author = ((v['authors'] as List?) ?? []).join(', '), year = _year(v['publishedDate']);
      out.add(Cand('${v['title']}', [author, year].where((e) => e.isNotEmpty).join(' · '), img.isEmpty ? null : img, {
        'author': author,
        'year': year,
        'pages': '${v['pageCount'] ?? ''}',
        'genre': ((v['categories'] as List?) ?? []).take(1).join(),
        'overview': '${v['description'] ?? ''}',
      }));
    }
  } catch (_) {}
  if (out.isNotEmpty) return out;
  final o = await fetchText('https://openlibrary.org/search.json?q=${Uri.encodeQueryComponent(q)}&limit=8&fields=title,author_name,first_publish_year,cover_i,number_of_pages_median,subject');
  try {
    for (final d in (jsonDecode(o ?? '{}')['docs'] as List? ?? [])) {
      final author = ((d['author_name'] as List?) ?? []).take(2).join(', '), year = '${d['first_publish_year'] ?? ''}';
      out.add(Cand('${d['title']}', [author, year].where((e) => e.isNotEmpty).join(' · '), d['cover_i'] == null ? null : 'https://covers.openlibrary.org/b/id/${d['cover_i']}-L.jpg', {
        'author': author,
        'year': year,
        'pages': '${d['number_of_pages_median'] ?? ''}',
        'genre': ((d['subject'] as List?) ?? []).take(1).join(),
        'overview': '',
      }));
    }
  } catch (_) {}
  return out;
}
