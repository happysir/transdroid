/*
 *	This file is part of Transdroid <http://www.transdroid.org>
 *	
 *	Transdroid is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *	
 *	Transdroid is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with Transdroid.  If not, see <http://www.gnu.org/licenses/>.
 *	
 */
package org.transdroid.daemon;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.transdroid.daemon.DaemonException.ExceptionType;
import org.transdroid.daemon.task.AddByFileTask;
import org.transdroid.daemon.task.AddByMagnetUrlTask;
import org.transdroid.daemon.task.AddByUrlTask;
import org.transdroid.daemon.task.DaemonTask;
import org.transdroid.daemon.task.DaemonTaskFailureResult;
import org.transdroid.daemon.task.DaemonTaskResult;
import org.transdroid.daemon.task.DaemonTaskSuccessResult;
import org.transdroid.daemon.task.GetFileListTask;
import org.transdroid.daemon.task.GetFileListTaskSuccessResult;
import org.transdroid.daemon.task.GetTorrentDetailsTask;
import org.transdroid.daemon.task.GetTorrentDetailsTaskSuccessResult;
import org.transdroid.daemon.task.RetrieveTask;
import org.transdroid.daemon.task.RetrieveTaskSuccessResult;
import org.transdroid.daemon.task.SetFilePriorityTask;
import org.transdroid.daemon.task.SetLabelTask;
import org.transdroid.daemon.util.DLog;

import android.net.Uri;

/**
 * A dummy adapter that does not communicate with some server, but maintains a local list of dummy data (reset every
 * time it is recreated) to simplify testing.
 * @author erickok
 */
public class DummyAdapter implements IDaemonAdapter {

	private static final String LOG_NAME = "Dummy daemon";

	private DaemonSettings settings;
	private List<Torrent> dummyTorrents;
	private List<Label> dummyLabels;

	/**
	 * Initialises a dummy adapter with some dummy data that may be manipulated.
	 */
	public DummyAdapter(DaemonSettings settings) {
		this.settings = settings;
		this.dummyTorrents = new ArrayList<Torrent>();
		this.dummyLabels = new ArrayList<Label>();
		String[] names = new String[] { "Documentary ", "Book ", "CD Image ", "Mix tape ", "App " };
		String[] labels = new String[] { "docs", "books", "isos", "music", "software" };
		TorrentStatus[] statuses = new TorrentStatus[] { TorrentStatus.Seeding, TorrentStatus.Downloading, TorrentStatus.Paused, TorrentStatus.Queued, TorrentStatus.Downloading, TorrentStatus.Seeding, TorrentStatus.Error };
		Random random = new Random();
		for (int i = 0; i < 25; i++) {
			String name = names[i % names.length] + Integer.toString(i);
			TorrentStatus status = statuses[i % statuses.length];
			int peersGetting = status == TorrentStatus.Downloading ? i * random.nextInt(16) : 0;
			int peersSending = status == TorrentStatus.Downloading ? i * random.nextInt(16) : 0;
			long size = (long) (1024D * 1024D * 1024D * i * random.nextDouble());
			long left = status == TorrentStatus.Downloading ? (long) ((double)(size) / random.nextDouble()) : 0;
			int rateDownload = status == TorrentStatus.Downloading ? (int) (1024D * 1024D * i * random.nextDouble())
					: 0;
			int rateUpload = status == TorrentStatus.Downloading || status == TorrentStatus.Seeding ? (int) (1024D * 1024D * i * random
					.nextDouble()) : 0;
			this.dummyTorrents.add(
					new Torrent(
							i, 
							"torrent_" + i, 
							name, 
							status, 
							"/downloads/" + name.replace(" ", "_"), 
							rateDownload, 
							rateUpload, 
							peersGetting, 
							peersSending, 
							peersGetting + peersSending, // Total connections
							(peersGetting + peersSending) * 2, // Twice the total connections
							(int) (status == TorrentStatus.Downloading?
									left / rateDownload: 0), // Eta
							size - left, 
							(long)((double)(size - left * 2) * random.nextDouble()), // Up to twice the amount downloaded
							size, 
							(float)(size - left) / size, // Part done
							1F, // Always 100% available
							labels[i % labels.length],
							new Date(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)), // Last week 
							null, 
							status == TorrentStatus.Error?
									"Dummy error": null,
							settings.getType()));
		}
		for (String label : labels) {
			// TODO: // Add count?
			dummyLabels.add(new Label(label, 0));
		}
	}

	@Override
	public DaemonTaskResult executeTask(DaemonTask task) {

		try {
			switch (task.getMethod()) {
			case Retrieve:

				return new RetrieveTaskSuccessResult((RetrieveTask) task, dummyTorrents, dummyLabels);

			case GetTorrentDetails:

				return new GetTorrentDetailsTaskSuccessResult((GetTorrentDetailsTask) task, new TorrentDetails(
						Arrays.asList("udp://tracker.com/announce:80", "https://torrents.org/announce:443"),
						task.getTargetTorrent().getStatusCode() == TorrentStatus.Error ? 
								Arrays.asList("Trackers not working.", "Files not available.") : null));

			case GetFileList:

				Torrent t = task.getTargetTorrent();
				List<TorrentFile> dummyFiles = new ArrayList<TorrentFile>();
				Priority priorities[] = new Priority[] { Priority.Normal, Priority.High, Priority.High, Priority.Low, Priority.Normal };
				for (int i = 0; i < 25; i++) {
					String fileName = "file_" + i + ".ext";
					// Every file has equal part in the total size
					long size = t.getTotalSize() / 25;
					long done = t.getDownloadedEver() / 25;
					Priority priority = priorities[i % priorities.length];
					dummyFiles.add(new TorrentFile("file_" + i, t.getName() + " file " + i, fileName, t
							.getLocationDir() + "/" + fileName, size, done, priority));
				}
				return new GetFileListTaskSuccessResult((GetFileListTask) task, dummyFiles);

			case AddByFile:

				String file = ((AddByFileTask) task).getFile();
				DLog.d(LOG_NAME, "Adding torrent " + file);
				File upload = new File(URI.create(file));
				dummyTorrents.add(new Torrent(0, "torrent_file", upload.getName(), TorrentStatus.Queued, "/downloads/"
						+ file, 0, 0, 0, 0, 0, 0, -1, 0, 0, 1024 * 1024 * 1000, 0, 1F, "isos", new Date(), null, null,
						settings.getType()));
				return new DaemonTaskSuccessResult(task);

			case AddByUrl:

				String url = ((AddByUrlTask) task).getUrl();
				DLog.d(LOG_NAME, "Adding torrent " + url);
				if (url == null || url.equals(""))
					throw new DaemonException(DaemonException.ExceptionType.ParsingFailed, "No url specified");
				Uri uri = Uri.parse(url);
				dummyTorrents.add(new Torrent(0, "torrent_byurl", uri.getLastPathSegment(), TorrentStatus.Queued,
						"/downloads/" + uri.getLastPathSegment(), 0, 0, 0, 0, 0, 0, -1, 0, 0, 1024 * 1024 * 1000, 0,
						1F, "music", new Date(), null, null, settings.getType()));
				return new DaemonTaskSuccessResult(task);

			case AddByMagnetUrl:

				String magnet = ((AddByMagnetUrlTask) task).getUrl();
				DLog.d(LOG_NAME, "Adding torrent " + magnet);
				Uri magnetUri = Uri.parse(magnet);
				dummyTorrents.add(new Torrent(0, "torrent_magnet", magnetUri.getLastPathSegment(),
						TorrentStatus.Queued, "/downloads/" + magnetUri.getLastPathSegment(), 0, 0, 0, 0, 0, 0, -1, 0,
						0, 1024 * 1024 * 1000, 0, 1F, "books", new Date(), null, null, settings.getType()));
				return new DaemonTaskSuccessResult(task);

			case Remove:

				dummyTorrents.remove(task.getTargetTorrent());
				return new DaemonTaskSuccessResult(task);

			case Pause:

				task.getTargetTorrent().mimicPause();
				return new DaemonTaskSuccessResult(task);

			case PauseAll:

				for (Torrent torrent: dummyTorrents) {
					torrent.mimicPause();
				}
				return new DaemonTaskSuccessResult(task);

			case Resume:
				
				task.getTargetTorrent().mimicPause();
				return new DaemonTaskSuccessResult(task);

			case ResumeAll:

				for (Torrent torrent: dummyTorrents) {
					torrent.mimicResume();
				}
				return new DaemonTaskSuccessResult(task);

			case Stop:
				
				task.getTargetTorrent().mimicStop();
				return new DaemonTaskSuccessResult(task);

			case StopAll:

				for (Torrent torrent: dummyTorrents) {
					torrent.mimicStop();
				}
				return new DaemonTaskSuccessResult(task);

			case Start:

				task.getTargetTorrent().mimicStart();
				return new DaemonTaskSuccessResult(task);

			case StartAll:

				for (Torrent torrent: dummyTorrents) {
					torrent.mimicStart();
				}
				return new DaemonTaskSuccessResult(task);

			case SetFilePriorities:

				SetFilePriorityTask prioTask = (SetFilePriorityTask) task;
				for (TorrentFile forFile : prioTask.getForFiles()) {
					forFile.mimicPriority(prioTask.getNewPriority());
				}
				return new DaemonTaskSuccessResult(task);

			case SetTransferRates:

				// No action, as the result in not visible anyway
				return new DaemonTaskSuccessResult(task);

			case SetLabel:

				SetLabelTask labelTask = (SetLabelTask) task;
				task.getTargetTorrent().mimicNewLabel(labelTask.getNewLabel());
				return new DaemonTaskSuccessResult(task);

			case SetTrackers:

				// TODO: Override the trackers?
				return new DaemonTaskSuccessResult(task);

			default:
				return new DaemonTaskFailureResult(task, new DaemonException(ExceptionType.MethodUnsupported,
						task.getMethod() + " is not supported by " + getType()));
			}
		} catch (DaemonException e) {
			return new DaemonTaskFailureResult(task, e);
		}
	}

	@Override
	public Daemon getType() {
		return settings.getType();
	}

	@Override
	public DaemonSettings getSettings() {
		return this.settings;
	}

}