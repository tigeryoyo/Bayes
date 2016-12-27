package com.tigeryoyo;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.tigeryoyo.util.SplitWordUtil;

/**
 * bayes分类器，利用多线程读取训练集进行训练，然后根据训练集的结果对文件进行分类。
 * bayes的输出为分出的若干类文件夹，每个类文件夹里有分类的结果。（可修改分类结果为各个被分类的文件集合）在这里的分类结果是一个txt文件，里面包含了被分进此类的文件名。
 * @author Chan
 *
 */
public class Classifier {
	/** 测试集文件夹路径 */
	private String testSetFolder;
	/** 默认结果集文件夹根路径 */
	private String resultFolder = "Bayes-Result";
	/** 存储每个单词在某一类下的条件概率 */
	private ConcurrentHashMap<String, double[]> termCdtnProMap;
	/** 存储各个类的信息 */
	private ConcurrentHashMap<Identifier, String[]> trSetInfoMap;

	public static void main(String[] args) {
		long current = System.currentTimeMillis();
		//将DataSet.zip解压到当前路径即可，或者自己有训练集测试集也行。
		Classifier classifier = new Classifier.Builder("training", "test").build();
		classifier.BayesClassifier();
		System.out.println(System.currentTimeMillis() - current);
	}

	/**
	 * @author Chan 使用构造者模式,选择是否设置结果集路径。
	 */
	public static class Builder {
		/** 训练集文件夹路径 */
		private String trainingSetFolder;
		/** 测试集文件夹路径 */
		private String testSetFolder;
		/** 结果集文件夹根路径 */
		private String resultFolder = "Bayes-Result";
		/** 存储每个单词在某一类下的条件概率 */
		private ConcurrentHashMap<String, double[]> termCdtnProMap;
		/** 存储各个类的信息 */
		private ConcurrentHashMap<Identifier, String[]> trSetInfoMap;

		/**
		 * 构造器，设置初始训练集路径与测试集路径。
		 */
		public Builder(String trainingSetFolder, String testSetFolder) {
			this.trainingSetFolder = trainingSetFolder;
			this.testSetFolder = testSetFolder;
		}

		public Builder setResultFolder(String resultFolder) {
			this.resultFolder = resultFolder;
			return this;
		}

		public Classifier build() {
			termCdtnProMap = Training.getInstance().training(trainingSetFolder).getTermCdtnProMap();
			trSetInfoMap = Training.getInstance().training(trainingSetFolder).getTrSetInfoMap();
			return new Classifier(this);
		}
	}

	/**
	 * 私有构造器。
	 */
	private Classifier(Builder builder) {
		this.testSetFolder = builder.testSetFolder;
		this.resultFolder = builder.resultFolder;
		this.termCdtnProMap = builder.termCdtnProMap;
		this.trSetInfoMap = builder.trSetInfoMap;
	}

	public void BayesClassifier() {
		BayesClassifier(testSetFolder);
	}

	/**
	 * 利用递归对输入的文件或文件集合进行贝叶斯分类。
	 * 
	 * @param testFile
	 */
	private void BayesClassifier(String testFile) {
		try {
			File srcFile = new File(testFile);
			if (srcFile.isDirectory()) {
				File[] files = srcFile.listFiles();
				for (File file : files) {
					BayesClassifier(file.getAbsolutePath());
				}
			} else {
				List<String> termList = SplitWordUtil.getFileSplit(srcFile);
				int classCounts = Integer.valueOf(trSetInfoMap.get(Identifier.CLASS_COUNTS)[0]);
				int wordBookCounts = termCdtnProMap.size();
				int maxIndex = -1;
				double maxProb = Double.NEGATIVE_INFINITY;
				for (int i = 0; i < classCounts; i++) {
					double totalCdtnProMap = Math.log(Double.valueOf(trSetInfoMap.get(Identifier.CLASS_RATIO)[i]));
					double crntClsTotalWords = Double.valueOf(trSetInfoMap.get(Identifier.TOTALWORDS)[i]);
					for (String term : termList) {
						double values[] = termCdtnProMap.get(term);
						if (values != null) {
							totalCdtnProMap += Math.log(values[i]);
						} else {
							totalCdtnProMap += 1 / (crntClsTotalWords + wordBookCounts);
						}
					}

					if (maxProb < totalCdtnProMap) {
						maxIndex = i;
						maxProb = totalCdtnProMap;
					}
				}
				String className = trSetInfoMap.get(Identifier.CLASS_NAME)[maxIndex];
				String resClsPath = resultFolder + File.separator + className;
				if (!new File(resClsPath).exists()) {
					new File(resClsPath).mkdirs();
				}
				File resFile = new File(resClsPath + File.separator + className + ".txt");
				FileWriter fw = new FileWriter(resFile, true);
				fw.write(testFile + "\n");
				fw.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
