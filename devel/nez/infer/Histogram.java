package nez.infer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Histogram {

	private List<DataUnit> dataUnits;
	private Map<Integer, DataUnit> dataMap; // key: token frequency
	private List<Integer> orderIdList;
	private final String label; // a label of the target token
	private int tmpTokenFrequency;
	private final int totalNumOfChunks;

	public Histogram(String label, int totalNumOfChunks) {
		this.label = label;
		this.totalNumOfChunks = totalNumOfChunks;
		this.dataMap = new HashMap<>();
		this.tmpTokenFrequency = 0;
		this.orderIdList = new ArrayList<>();
	}

	public Histogram(String label, int totalNumOfChunks, List<DataUnit> dataUnits) {
		this.label = label;
		this.totalNumOfChunks = totalNumOfChunks;
		this.dataUnits = dataUnits;
	}

	public final String getLabel() {
		return label;
	}

	public final List<Integer> getOrderIdList() {
		return orderIdList;
	}

	public final void commit() {
		if (!dataMap.containsKey(tmpTokenFrequency)) {
			dataMap.put(tmpTokenFrequency, new DataUnit(tmpTokenFrequency));
		}
		dataMap.get(tmpTokenFrequency).updateChunkCount();
		this.tmpTokenFrequency = 0;

	}

	public void update() {
		this.tmpTokenFrequency++;
	}

	public void update(int id) {
		this.tmpTokenFrequency++;
		if (!orderIdList.contains(id)) {
			orderIdList.add(id);
		}
	}

	public final int width() {
		return dataUnits.size();
	}

	private int wholeChunkSize() {
		return totalNumOfChunks;
	}

	public void normalize() {
		newDataUnits();
		orderByTokenFrequency();
	}

	private void newDataUnits() {
		this.dataUnits = new ArrayList<>();
		for (Entry<Integer, DataUnit> unit : dataMap.entrySet()) {
			dataUnits.add(unit.getValue());
		}
	}

	private void orderByTokenFrequency() {
		dataUnits.sort((unit1, unit2) -> {
			int subOfTokenFrequency = unit2.getTokenFrequency() - unit1.getTokenFrequency();
			int subOfChunkCount = unit2.getChunkCount() - unit1.getChunkCount();
			if (subOfChunkCount == 0) {
				return 0;
			} else {
				return subOfTokenFrequency;
			}
		});
	}

	private int getChunkCountI(int idx) {
		return idx < width() ? dataUnits.get(idx).getChunkCount() : 0;
	}

	private double getChunkCountF(int idx) {
		return idx < width() ? dataUnits.get(idx).getChunkCount() : 0;
	}

	public final double residualMass(int idx) {
		int rm = 0;
		for (int i = idx + 1; i < width(); i++) {
			rm += getChunkCountI(i);
		}
		return (double) rm / wholeChunkSize();
	}

	public final double coverage() {
		double cov = 0.0;
		for (int i = 0; i < width(); i++) {
			cov += getChunkCountI(i);
		}

		return cov / wholeChunkSize();
	}

	protected static double calcRelativeEntropy(Histogram h1, Histogram h2) {
		double relativeEntropy = 0.0;
		double f1, f2;
		for (int i = 0; i < h1.width(); i++) {
			f1 = h1.getChunkCountF(i);
			f2 = h2.getChunkCountF(i);

			relativeEntropy += (f1 / h1.wholeChunkSize()) * Math.log(f1 / f2);
		}

		return relativeEntropy;
	}

	public static double calcSimilarity(Histogram h1, Histogram h2) {
		Histogram ave = average(h1, h2);
		return calcRelativeEntropy(h1, ave) / 2 + (calcRelativeEntropy(h2, ave) / 2);
	}

	public static Histogram average(Histogram h1, Histogram h2) {
		List<DataUnit> newBody = new ArrayList<>();
		int[] sums = new int[Math.max(h1.width(), h2.width())];
		for (int i = 0; i < sums.length; i++) {
			sums[i] += h1.getChunkCountI(i);
			sums[i] += h2.getChunkCountI(i);
			newBody.add(new DataUnit(0, sums[i] / 2));
		}
		String label = String.format("AVE_%s_%s", h1.getLabel(), h2.getLabel());
		return new Histogram(label, h1.totalNumOfChunks, newBody);
	}
}

class DataUnit {
	private final int tokenFrequency;
	private int chunkCount;
	private List<Integer> orderIdList;

	public DataUnit(int tokenFrequency, int chunkCount) {
		this.tokenFrequency = tokenFrequency;
		this.chunkCount = chunkCount;
	}

	public DataUnit(int tokenFrequency) {
		this.tokenFrequency = tokenFrequency;
		this.chunkCount = 0;
	}

	public int getTokenFrequency() {
		return tokenFrequency;
	}

	public int getChunkCount() {
		return chunkCount;
	}

	public void setOrderIdList(List<Integer> list) {
		this.orderIdList = list;
	}

	public List<Integer> getOrderIdList() {
		return orderIdList;
	}

	public void updateChunkCount() {
		this.chunkCount++;
	}
}
