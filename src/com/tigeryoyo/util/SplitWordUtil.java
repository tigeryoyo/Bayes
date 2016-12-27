package com.tigeryoyo.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.ansj.util.FilterModifWord;
import org.apache.log4j.Logger;

/**
 * @author Chan
 * @description 利用Ansj分词工具将文本分词 {@link} http://nlpchina.github.io/ansj_seg/
 */
public class SplitWordUtil {
	
	private static final Logger logger = Logger.getLogger(SplitWordUtil.class);
	
	private SplitWordUtil() {
	}
	
	static {
		synchronized (SplitWordUtil.class) {
			File stopwords = new File("library/stopwords.dic");
			try {
				FileReader fr = new FileReader(stopwords);
				BufferedReader br = new BufferedReader(fr);

				String line;
				while ((line =br.readLine())!= null) {
					FilterModifWord.insertStopWord(line);
				}
				br.close();
				fr.close();
			} catch (IOException e) {
				logger.info("停用词文件");
			}
		}
	}

	/**
	 * 将文本进行分词。
	 * 
	 * @param file 输入的文本文件
	 * @return
	 */
	public static List<String> getFileSplit(File file) {
		List<String> finalRes = new ArrayList<String>();
		try {
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String line = null;
			while ((line = br.readLine()) != null) {
				List<Term> splitRes = ToAnalysis.parse(line);
				splitRes = FilterModifWord.modifResult(splitRes);
				for (Term t : splitRes) {
					finalRes.add(t.getName());
				}
			}
			br.close();
			fr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return finalRes;
	}
}
