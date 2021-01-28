package nez.parser;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import nez.util.ConsoleUtils;
import nez.util.UList;
import nez.util.UMap;
import nez.util.Verbose;

public class ParserProfiler {
	final String logFile;

	public ParserProfiler(String logFile) {
		this.logFile = logFile;
	}

	static class DataPoint {
		String key;
		Object value;

		DataPoint(String key, Object value) {
			this.key = key;
			this.value = value;
		}
	}

	private final UList<DataPoint> dataPointList = new UList<>(new DataPoint[64]);
	private final UMap<DataPoint> dataPointMap = new UMap<>();

	private void setDataPoint(String key, Object value) {
		if (!dataPointMap.hasKey(key)) {
			DataPoint d = new DataPoint(key, value);
			dataPointMap.put(key, d);
			dataPointList.add(d);
		} else {
			DataPoint d = dataPointMap.get(key);
			d.value = value;
		}
	}

	public final void setText(String key, String value) {
		setDataPoint(key, value);
	}

	public final void setFile(String key, String file) {
		int loc = file.lastIndexOf('/');
		if (loc > 0) {
			file = file.substring(loc + 1);
		}
		setDataPoint(key, file);
	}

	public final void setCount(String key, long v) {
		setDataPoint(key, v);
	}

	public final void setDouble(String key, double d) {
		setDataPoint(key, d);
	}

	public final void setRatio(String key, long v, long v2) {
		setDataPoint(key, (double) v / (double) v2);
	}

	public final String formatCommaSeparateValue() {
		StringBuilder sb = new StringBuilder();
		SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd");
		sb.append(sdf1.format(new Date()));
		for (DataPoint d : dataPointList) {
			sb.append(",");
			sb.append(d.key);
			sb.append(":,");
			if (d.value instanceof Double) {
				sb.append(String.format("%.5f", d.value));
			} else {
				sb.append(d.value);
			}
		}
		return sb.toString();
	}

	public final void log() {
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
			String csv = formatCommaSeparateValue();
			Verbose.println("writing .. " + logFile + " " + csv);
			out.println(csv);
		} catch (IOException e) {
			ConsoleUtils.exit(1, "Can't write csv log: " + logFile);
		}
	}

	public static void recordLatencyMS(ParserProfiler rec, String key, long nanoT1, long nanoT2) {
		if (rec != null) {
			long t = (nanoT2 - nanoT1) / 1000; // [micro second]
			rec.setDouble(key + "[ms]", t / 1000.0);
		}
	}

	public static void recordThroughputKPS(ParserProfiler rec, String key, long length, long nanoT1, long nanoT2) {
		if (rec != null) {
			long micro = (nanoT2 - nanoT1) / 1000; // [micro second]
			double sec = micro / 1000000.0;
			double thr = length / sec / 1024;
			rec.setDouble(key + "[KiB/s]", thr);
		}
	}

}
