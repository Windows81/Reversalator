package A.VisualPlugin.Phone_001_714_463_5142;

import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.ResponseHandler;

public class StaticMethods {
	public static String rootPath = Environment.DIRECTORY_MUSIC + "/SAVED";
	
	public static SyncHttpClient client;
	public static Application context;
	
	static {
		context = MainActivity.MainContext;
		client = new SyncHttpClient();
	}
	
	public static String Time() {
		return new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ").format(new Date());
	}
	
	public static String getSongName(String artist, String song) {
		return (artist.length() > 0 ? artist + " - " : "") + song;
	}
	
	public static String getAlbumName(String artist, String album) {
		return rootPath + '/' + ((artist.length() > 0 ? artist + " - " : "") + album).toUpperCase();
	}
	
	public static String getNameFromPath(String path) {
		return path.substring(path.lastIndexOf('\\') + 4, path.length() - path.lastIndexOf('\\') - 9);
	}
	
	public static Boolean IsReversedFile(String path) {
		return path.endsWith("r.wav");
	}
	
	public static ArrayList<String> getBingResultUrls(String query, int pages) {
		ArrayList<String> list = new ArrayList<String>();
		for (int c = 0; c <= pages * 10; c += 10) {
			client.get("https://www.bing.com/search?q=" + URLEncoder.encode(query) + "&first=" + c, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, Header[] headers, byte[] response) {
					synchronized (list) {
						Matcher matcher = Pattern.compile("<h2>\\s*<a href=\"(http.*?)\" h").matcher(new String(response));
						while (matcher.find())
							list.add(matcher.group(1));
					}
				}
				
				@Override
				public void onFailure(int statusCode, Header[] headers, byte[] response, Throwable error) {
				}
			});
		}
		return list;
	}
	
	public static ArrayList<String> getPolledAlbumResults(String artist, String album, int pages) {
		ArrayList<String> urls = getBingResultUrls(String.format(
				"download \"{0}\" \"{1}\" contains:.zip", artist, album), pages),
				ret = new ArrayList<String>();
		Collections.sort(urls, (a, b) -> {
			return getURLRank(b) - getURLRank(a);
		});
		
		for (int c = 0; c < urls.size(); c++) {
			String url = urls.get(c);
			if (url.contains("vk.com")) {
				decrement:
				urls.remove(c--);
				continue;
			}
			
			client.get(url, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, Header[] headers, byte[] response) {
					Matcher matcher = Pattern.compile("http.{7,127}\\.zip").matcher(new String(response));
					while (matcher.find())
						ret.add(matcher.group());
				}
				
				@Override
				public void onFailure(int statusCode, Header[] headers, byte[] response, Throwable error) {
				}
			});
		}
		return ret;
	}
	
	public static Playlist getAlbum(String artist, String album, String url) {
		Playlist p;
		if ((p = getSavedAlbum(artist, album)) != null) return p;
		else if ((p = getAlbumFromZip(artist, album, url)) != null) return p;
		else if ((p = SaveAlbumFromZip(artist, album, url)) != null) return p;
		return null;
	}
	
	public static Playlist getAlbum(String artist, String album) {
		Playlist p = getSavedAlbum(artist, album);
		if (p != null) return p;
		
		for (String url : getPolledAlbumResults(artist, album, 1))
			if ((p = SaveAlbumFromZip(artist, album, url)) != null)
				return p;
		return null;
	}
	
	private static Playlist getSavedAlbum(String artist, String album) {
		Playlist A = new Playlist();
		String aFolderPath = getAlbumName(artist, album);
		File dir = new File(aFolderPath);
		if (dir.exists() && dir.isDirectory()) {
			for (File file : dir.listFiles())
				A.add(new SongInfo(getNameFromPath(file.getPath()), null, artist, file.getPath()));
			return A;
		}
		return null;
	}
	
	//Modified from https://stackoverflow.com/questions/3382996/how-to-extractMP3s-files-programmatically-in-android
	private static boolean extractMP3s(String path, String aFolderPath) {
		InputStream is;
		ZipInputStream zis;
		try {
			String filename;
			is = new FileInputStream(path);
			zis = new ZipInputStream(new BufferedInputStream(is));
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;
			
			while ((ze = zis.getNextEntry()) != null) {
				String fn = ze.getName();
				filename = fn.
						replace('\\', '/').
						substring(fn.lastIndexOf('/'));
				
				// Need to create directories if not exists, or
				// it will generate an Exception...
				if (ze.isDirectory() && !ze.getName().toLowerCase().endsWith(".mp3"))
					continue;
				
				FileOutputStream fout = new FileOutputStream(aFolderPath + '/' + filename);
				while ((count = zis.read(buffer)) != -1) {
					fout.write(buffer, 0, count);
				}
				
				fout.close();
				zis.closeEntry();
			}
			
			zis.close();
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	private static Playlist SaveAlbumFromZip(String artist, String album, String url) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
		req.setDestinationInExternalPublicDir(rootPath, "ziPPy.zip.zip");
		req.setVisibleInDownloadsUi(true);
		Thread thr = Thread.currentThread();
		long ref = dm.enqueue(req);
		
		BroadcastReceiver rec[] = new BroadcastReceiver[1];
		rec[0] = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != ref)
					return;
				context.unregisterReceiver(rec[0]);
				thr.notify();
			}
		};
		
		context.registerReceiver(rec[0], new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		try {
			thr.wait();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return getAlbumFromZip(artist, album, rootPath + "/ziPPy.zip.zip");
	}
	
	public static Playlist getAlbumFromZip(String artist, String album, String path) {
		String aFolderPath = getAlbumName(artist, album);
		if (!extractMP3s(path, aFolderPath))
			return null;
		
		Playlist A = new Playlist();
		File[] entries = new File(aFolderPath).listFiles();
		for (int c = 0; c < entries.length; c++) {
			String mp3P = entries[c].getName();
			String wavP = aFolderPath + String.format("/%02d %s f.wav", c + 1,
					mp3P.substring(0, mp3P.length() - 4));
			//ConvertStream()
		}
		return A;
	}
	
	//	private static Playlist getAlbumFromZip(String artist, String album, ZipArchive archive) {
//		var A = new Playlist();
//		ArrayList<ZipArchiveEntry> entries = null;
//		ArrayList<String> entryNames = null, entryUrls = null;
//		var aFolderPath = getAlbumName(artist, album);
//		if ((entryNames = TrimSongNames(entryUrls = (entries = archive
//				.Entries.Where(s = > s.FullName.ToLower().EndsWith("mp3")).ToArrayList())
//				.Select(s = > s.FullName).ToArrayList()).ToArrayList()).Count< 2)
//		return null;
//
//		Array.ForEach(Directory.CreateDirectory(aFolderPath).getFiles()
//				.Select(f = > f.FullName).ToArray(), File.Delete);
//		for (int c = 0; c < entries.Count; c++) {
//			var str = entries[c].Open();
//			var mp3P = aFolderPath + "/ALBUMDL" + c + ".mp3";
//			var path = aFolderPath + String.Format("/{0:D2} {1} f.wav", c + 1, entryNames[c]);
//			var fs = new FileStream(mp3P, FileMode.Create);
//			str.CopyTo(fs);
//			str.Dispose();
//			fs.Dispose();
//			ConvertStream(mp3P, path);
//			File.Delete(mp3P);
//
//			A.Add(new SongInfo() {
//				artist=artist,
//				songName=entryNames[c],
//				songUrl=entryUrls[c],
//				filePath=path,
//			});
//			str.Dispose();
//		}
//		return A;
//	}
//
//	private static ArrayList<String> TrimSongNames(ArrayList<String> ArrayList) {
//		int a, b;
//		String r = ArrayList[0];
//		for (a = 0; ArrayList.All(s = > s[a] == r[a] || (char.IsNumber(s[a]) && char.IsNumber(r[a])))
//			;
//		a++);
//		for (b = 0; ArrayList.All(s = > s[s.length() - b - 1] == r[r.length() - b - 1] ||
//				(char.IsNumber(s[s.length() - b - 1]) && char.IsNumber(r[r.length() - b - 1])))
//			; b++);
//		return ArrayList.Select(s = > {
//				s = s.SubString(a, s.length() - b - a);
//		var ftIndex = Regex.Match(s, "( \\()?f(ea)?t") ?.Index ??0;
//		if (ftIndex == 0) ftIndex = s.length();
//		s = s.SubString(0, ftIndex);
//		return s;
//		}).ToArrayList();
//	}
//
//	public static Playlist getSong(String artist, String song, int start=1, int end=20) {
//		Playlist p = getSavedSong(artist, song);
//		if (p != null)
//			return p;
//		foreach(var url in getSongResults(artist, song, start, end))
//		if ((p = SaveSong(artist, song, url)) != null)
//			return p;
//		return null;
//	}
//
//	private static Playlist getSavedSong(String artist, String song) {
//		var f = Directory.getFiles(rootPath, getSongName(artist, song) + "*.wav", SearchOption.AllDirectories);
//		return f.length() > 0 ? new Playlist(new SongInfo() {
//			artist=artist,songName=song,filePath=f[0]
//		}) : null;
//	}
//
//	private static Playlist SaveSong(String artist, String song, String url) {
//		var t = Time();
//		String mp3P = rootPath + '/' + "DOWNLOAD" + t + ".mp3";
//		String path = rootPath + '/' + artist + " - " + song + " f.wav";
//		FileStream sw = null;
//		try {
//			cl.OpenRead(url).CopyTo(sw = new FileStream(mp3P, FileMode.Create));
//			sw.Dispose();
//			ConvertStream(mp3P, path);
//			File.Delete(mp3P);
//			return new Playlist(new SongInfo() {
//				songName=song,
//				artist=artist,
//				filePath=path,
//				songUrl=url,
//			});
//		} catch (InvalidDataException) {
//		} catch (ArgumentException) {
//		} catch (WebException) {
//		}
//		return null;
//	}
//
//	public static ArrayList<String> getSongResults(String artist, String song, int start=1, int end=20) {
//		ArrayList<String> urls = getBingResultUrls("\"{0}\" \"{1}\" contains:mp3", start, end);
//		return urls.SelectMany(url = >
//				{
//		try {
//			return Regex.Matches(new StreamReader(cl.OpenRead(url)).ReadToEnd(),
//			@ "http.{7,127}\.mp3").Cast<Match> ().Select(mt2 = > mt2.Value);
//		} catch (Exception) {
//			return new ArrayList<String>();
//		}
//		}).Distinct().OrderByDescending(s = > getURLRank(s, artist, song)).ToArrayList();
//	}
//
	public static String SpacifyURL(String s) {
		return URLDecoder.decode(s).replace('-', ' ').replace('_', ' ').replaceAll(" {2,127}", " ");
	}
	
	public static int getURLRank(String s) {
		return getURLRank(s, "", "");
	}
	
	public static int getURLRank(String s, String artist, String song) {
		String l = SpacifyURL(s).toLowerCase();
		String[] sA = l.split("/");
		String n = sA[sA.length - 1];
		int v = 0;
		if (n.contains(song.toLowerCase()))
			v += 4;
		else if (l.contains(song.toLowerCase()))
			v += 3;
		if (n.contains(artist.toLowerCase()))
			v += 2;
		
		if (artist != "")
			l = l.replace(artist.toLowerCase(), "");
		if (song != "")
			l = l.replace(song.toLowerCase(), "");
		
		if (l.contains("instrumental") || l.contains("mix") || l.contains("flip")
				|| (l.contains("chopped") && l.contains("screw")) || l.contains("acoustic"))
			v -= 7;
		if (l.contains("clean"))
			v--;
		if (l.contains("1604ent"))//IT DOWNLOADS LIKE SÜPER SLOTH!
			v = 0;
		if (l.contains("brickhomedesign"))//IT DOWNLOADS SUPER WRONG!
			v = 0;
		if (l.contains("songslover"))//Inconsistent encoding?????
			v = 0;
		if (l.contains("soundike"))//SHORT SAMPLES EVERYWHERE
			v = 0;
		if (l.contains("lq"))
			v *= -1;
		/*if (v > 3 && l.matches("\\([^\\.]+\\)"))
			v = 0;
			*/
		return v;
	}
//
//	public static void ConvertStream(String input, String output) {
//		using(var mp3 = new Mp3FileReader(input))
//		using(var wfc = WaveFormatConversionStream.CreatePcmStream(mp3))
//		WaveFileWriter.CreateWaveFile(output, wfc);
//	}
//
//	public static void ReverseStream(String input, String output) {
//		int c = 0;
//		const int b = 4;
//		var reader = new WaveFileReader(input);
//		//Why is 'b' set to 4?  Since the input file gets converted into a WAV anyway, it might as well have the same wave format.
//		var m = new WaveFileWriter(output, reader.WaveFormat);
//		var i = reader.length() / b;
//		var l = new byte[i][];
//		var f = new byte[b];
//		do {
//			var a = new byte[b];
//			reader.Read(f, 0, f.length());
//			Array.Copy(f, a, b);
//			l[--i] = a;
//		} while ((c += f.length()) < reader.length());
//		foreach(var a in l)
//		m.Write(a, 0, b);
//		//var w = new WaveFileWriter(s, reader.WaveFormat);
//		reader.Dispose();
//		m.Dispose();
//	}
}
