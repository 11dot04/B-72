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

Future<dynamic> _json(String url) async {
  final t = await fetchText(url);
  if (t == null) return null;
  try {
    return jsonDecode(t);
  } catch (_) {
    return null;
  }
}

// The iTunes Search API needs no key. Movies need media=movie as well as the entity.
Future<List<dynamic>> _itunes(String q, String media, String entity) async {
  final j = await _json('https://itunes.apple.com/search?term=${Uri.encodeQueryComponent(q)}&media=$media&entity=$entity&limit=8');
  return j is Map && j['results'] is List ? j['results'] as List : [];
}

/// Films: iTunes first, then Wikipedia (no key, good posters and one-line facts) if iTunes is empty or down.
Future<List<Cand>> searchFilms(String q) async {
  final out = <Cand>[
    for (final r in await _itunes(q, 'movie', 'movie'))
      if (r['trackName'] != null)
        Cand('${r['trackName']}', '${_year(r['releaseDate'])} · ${r['artistName'] ?? ''}', _art(r['artworkUrl100'], 600), {
          'year': _year(r['releaseDate']),
          'director': '${r['artistName'] ?? ''}',
          'genre': '${r['primaryGenreName'] ?? ''}',
          'runtime': r['trackTimeMillis'] is num ? '${((r['trackTimeMillis'] as num) / 60000).round()}' : '',
          'overview': '${r['longDescription'] ?? r['shortDescription'] ?? ''}',
        }),
  ];
  return out.isNotEmpty ? out : _wikiFilms(q);
}

Future<List<Cand>> _wikiFilms(String q) async {
  final out = <Cand>[];
  final j = await _json('https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=8'
      '&gsrsearch=${Uri.encodeQueryComponent('$q film')}&prop=pageimages%7Cextracts%7Cdescription'
      '&piprop=thumbnail&pithumbsize=600&exintro=1&explaintext=1&exsentences=3&exlimit=max');
  try {
    final pages = ((j['query']['pages'] as Map).values.toList())..sort((a, b) => ((a['index'] as num?) ?? 99).compareTo((b['index'] as num?) ?? 99));
    for (final p in pages) {
      final d = '${p['description'] ?? ''}';
      if (!d.toLowerCase().contains('film')) continue;
      final year = RegExp(r'\b(1[89]\d\d|20\d\d)\b').firstMatch(d)?[1] ?? '';
      final dir = RegExp(r'\bby ([^,;(]+)').firstMatch(d)?[1]?.trim() ?? '';
      final title = '${p['title']}'.replaceAll(RegExp(r'\s*\([^)]*film\)$', caseSensitive: false), '');
      final thumb = p['thumbnail'] is Map ? '${p['thumbnail']['source']}' : null;
      out.add(Cand(title, '$year · $dir', thumb, {'year': year, 'director': dir, 'genre': '', 'runtime': '', 'overview': _clean('${p['extract'] ?? ''}')}));
    }
  } catch (_) {}
  return out;
}

Future<List<Cand>> searchTracks(String q) async => [
      for (final r in await _itunes(q, 'music', 'song'))
        if (r['trackName'] != null)
          Cand('${r['trackName']}', '${r['artistName'] ?? ''} · ${r['collectionName'] ?? ''}', _art(r['artworkUrl100'], 600), {
            'artist': '${r['artistName'] ?? ''}',
            'album': '${r['collectionName'] ?? ''}',
            'year': _year(r['releaseDate']),
            'genre': '${r['primaryGenreName'] ?? ''}',
          }),
    ];

/// Books: Open Library first (covers by id), Google Books as a fallback. Neither needs a key.
Future<List<Cand>> searchBooks(String q) async {
  final out = <Cand>[];
  final j = await _json('https://openlibrary.org/search.json?q=${Uri.encodeQueryComponent(q)}&limit=10'
      '&fields=title,author_name,first_publish_year,cover_i,number_of_pages_median,subject');
  try {
    for (final d in j['docs'] as List) {
      if (d['title'] == null) continue;
      final au = d['author_name'] is List && (d['author_name'] as List).isNotEmpty ? '${(d['author_name'] as List).first}' : '';
      final yr = d['first_publish_year'] == null ? '' : '${d['first_publish_year']}';
      final sub = d['subject'] is List && (d['subject'] as List).isNotEmpty ? '${(d['subject'] as List).first}' : '';
      out.add(Cand('${d['title']}', '$au · $yr', d['cover_i'] == null ? null : 'https://covers.openlibrary.org/b/id/${d['cover_i']}-L.jpg', {
        'author': au,
        'year': yr,
        'pages': d['number_of_pages_median'] == null ? '' : '${d['number_of_pages_median']}',
        'genre': sub,
      }));
    }
  } catch (_) {}
  // books with a cover first, keeping the relevance order otherwise
  out.sort((a, b) => ((a.image == null) ? 1 : 0).compareTo((b.image == null) ? 1 : 0));
  if (out.isNotEmpty) return out.take(8).toList();

  final g = await _json('https://www.googleapis.com/books/v1/volumes?q=${Uri.encodeQueryComponent(q)}&maxResults=8');
  try {
    for (final it in g['items'] as List) {
      final v = it['volumeInfo'] as Map;
      if (v['title'] == null) continue;
      final au = v['authors'] is List && (v['authors'] as List).isNotEmpty ? '${(v['authors'] as List).first}' : '';
      final yr = _year(v['publishedDate']);
      final img = v['imageLinks'] is Map ? '${v['imageLinks']['thumbnail']}'.replaceFirst('http://', 'https://').replaceFirst('zoom=1', 'zoom=2') : null;
      out.add(Cand('${v['title']}', '$au · $yr', img, {
        'author': au,
        'year': yr,
        'pages': v['pageCount'] == null ? '' : '${v['pageCount']}',
        'genre': v['categories'] is List && (v['categories'] as List).isNotEmpty ? '${(v['categories'] as List).first}' : '',
      }));
    }
  } catch (_) {}
  return out;
}

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
