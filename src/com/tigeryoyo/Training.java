package com.tigeryoyo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.tigeryoyo.util.SplitWordUtil;

/**
 * 对训练集进行测试。
 * 
 * @author Chan
 *
 */
public class Training {
	/** 存储各个类的词频 */
	private ConcurrentHashMap<String, double[]> wordCountMap;
	/** 存储每个单词在某一类下的条件概率 */
	private ConcurrentHashMap<String, double[]> termCdtnProMap;
	/** 存储各个类的信息 */
	private ConcurrentHashMap<Identifier, String[]> trSetInfoMap;
	/** 缓存训练集路径 */
	private String cachePath;
	/** 类的个数 */
	private int classCounts;
	/** 静态实例，保证只有一个类实例 */
	private static Training instance;

	/**
	 * 私有构造器，为了单例模式。
	 */
	private Training() {
	};

	/**
	 * 获取类实例。
	 * 
	 * @return
	 */
	public static Training getInstance() {
		synchronized (Training.class) {
			if (instance == null) {
				instance = new Training();
			}
			return instance;
		}
	}

	/**
	 * 获取termCdtnProMap
	 * 
	 * @return
	 */
	public ConcurrentHashMap<String, double[]> getTermCdtnProMap() {
		return termCdtnProMap;
	}

	/**
	 * 获取trSetInfoMap
	 * 
	 * @return
	 */
	public ConcurrentHashMap<Identifier, String[]> getTrSetInfoMap() {
		return trSetInfoMap;
	}

	/**
	 * 对训练集进行训练
	 * 
	 * @param trainingSetFolder
	 *            训练集文件夹路径
	 * @return
	 */
	public Training training(String trainingSetFolder) {
		if (isModified(trainingSetFolder)) {
			cachePath = trainingSetFolder;
			try {
				File srcFolder = new File(trainingSetFolder);
				if (!srcFolder.exists() || !srcFolder.isDirectory()) {
					throw new FileNotFoundException("请检查路径：" + srcFolder.getAbsolutePath());
				}

				File[] classFolders = srcFolder.listFiles();
				if (check(classFolders)) {
					wordCountMap = new ConcurrentHashMap<String, double[]>();
					trSetInfoMap = new ConcurrentHashMap<Identifier, String[]>();
					classCounts = classFolders.length;
					/* 固定线程数线程池,实现多线线程读取文件夹 */
					ExecutorService fixedThreadPool;
					if (classCounts < 7) {
						fixedThreadPool = Executors.newFixedThreadPool(classCounts);
					} else {
						fixedThreadPool = Executors.newFixedThreadPool(7);
					}

					for (int i = 0; i < classCounts; i++) {
						fixedThreadPool.execute(new ReaderThread(i, classCounts, classFolders[i].getAbsolutePath()));
					}
					// 关闭线程池,只是不能提交新任务、等待执行的任务不受影响。
					fixedThreadPool.shutdown();
					// 先调用shutdown()关闭线程池(线程池中的线程继续执行)
					// 循环判断线程池的线程是否执行完毕，执行完毕返回true
					while (!fixedThreadPool.isTerminated())
						;
					calcConditionalProbability();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return this;
	}

	/**
	 * 判断是否训练的同一训练集，若相同则返回false
	 * 
	 * @param srcPath
	 * @return
	 */
	private boolean isModified(String srcPath) {
		if (cachePath == null) {
			return true;
		}
		return cachePath != srcPath;
	}

	/**
	 * 计算单词term在某一类下的条件概率。
	 */
	private void calcConditionalProbability() {
		termCdtnProMap = new ConcurrentHashMap<String, double[]>();
		double wordBookCounts = wordCountMap.size();
		for (int i = 0; i < classCounts; i++) {
			double classTotalWords = Double.valueOf(trSetInfoMap.get(Identifier.TOTALWORDS)[i]);
			for (Entry<String, double[]> key_value : wordCountMap.entrySet()) {
				String key = key_value.getKey();
				double[] values = termCdtnProMap.get(key);
				if (i == 0) {
					values = new double[classCounts];
				}
				values[i] = (key_value.getValue()[i] + 1) / (wordBookCounts + classTotalWords);
				termCdtnProMap.put(key, values);
			}
		}

		/*
		 * 输入ratio与classcounts信息
		 */
		String[] ratio = new String[classCounts];
		double sum = 0.0;
		for (int i = 0; i < classCounts; i++) {
			sum += Double.valueOf(trSetInfoMap.get(Identifier.FILESCOUNT)[i]);
		}
		for (int i = 0; i < classCounts; i++) {
			ratio[i] = String.valueOf(Double.valueOf(trSetInfoMap.get(Identifier.FILESCOUNT)[i]) / sum);
		}
		trSetInfoMap.put(Identifier.CLASS_RATIO, ratio);
		trSetInfoMap.put(Identifier.CLASS_COUNTS, new String[] { String.valueOf(classCounts) });
	}

	/**
	 * 读文件线程工具内部类。
	 * 
	 * @author Chan
	 *
	 */
	private class ReaderThread extends Thread {
		/** 类id */
		private int index;
		/** 类的个数 */
		private int classCounts;
		/** 类路径 */
		private String folderPath;

		public ReaderThread(int index, int classCounts, String folderPath) {
			this.index = index;
			this.classCounts = classCounts;
			this.folderPath = folderPath;
		}

		@Override
		public void run() {
			try {
				File[] files = new File(folderPath).listFiles();
				setKey_ValueToMap(folderPath.substring(folderPath.lastIndexOf(File.separator) + 1, folderPath.length()),
						Identifier.CLASS_NAME);
				setKey_ValueToMap(folderPath, Identifier.CLASS_PATH);
				setKey_ValueToMap(String.valueOf(index), Identifier.CLASS_INDEX);
				setKey_ValueToMap(String.valueOf(files.length), Identifier.FILESCOUNT);

				for (File file : files) {
					// 文本分词
					List<String> terms = SplitWordUtil.getFileSplit(file);
					for (String term : terms) {
						synchronized ("lock") {
							double[] values = wordCountMap.get(term);
							if (values == null) {
								values = new double[classCounts];
							}
							values[index] += 1;
							wordCountMap.put(term, values);
						}
					}
					synchronized ("lock") {
						String[] values = trSetInfoMap.get(Identifier.TOTALWORDS);
						if (values == null) {
							values = new String[classCounts];
						}

						if (values[index] == null) {
							values[index] = "0";
						}
						values[index] = String.valueOf(Integer.valueOf(values[index]) + terms.size());
						trSetInfoMap.put(Identifier.TOTALWORDS, values);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * 写入值到trSetInfoMap中。
		 * 
		 * @param parm
		 * @param identifier
		 */
		private void setKey_ValueToMap(String parm, Identifier identifier) {
			synchronized ("lock") {
				String[] values = trSetInfoMap.get(identifier);
				if (values == null) {
					values = new String[classCounts];
				}
				values[index] = parm;
				trSetInfoMap.put(identifier, values);
			}
		}
	}

	/**
	 * 检查输入的训练集文件夹是否符合标准。
	 * 
	 * @param trainingSetFolders
	 * @return 训练集根目录下的每一个文件都是文件夹则返回true，否则返回false。
	 */
	private boolean check(File[] trainingSetFolder) {
		for (File file : trainingSetFolder) {
			if (!file.isDirectory()) {
				return false;
			}
		}

		return true;
	}
}

/**
 * 枚举类型，标识属性。
 * 
 * @author Chan
 *
 */
enum Identifier {
	CLASS_NAME, CLASS_PATH, CLASS_INDEX, CLASS_RATIO, FILESCOUNT, TOTALWORDS, CLASS_COUNTS
};