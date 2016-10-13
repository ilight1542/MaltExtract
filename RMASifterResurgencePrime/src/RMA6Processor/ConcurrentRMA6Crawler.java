package RMA6Processor;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import NCBI_MapReader.NCBI_MapReader;

public class ConcurrentRMA6Crawler implements Callable<RMA6BlastCrawler> {
	private String inDir;
	private String fileName;
	private String speciesName;
	private NCBI_MapReader mapReader;
	private String outDir;
	private Logger warning;
	public ConcurrentRMA6Crawler(String dir, String name, String species, String out, NCBI_MapReader reader,Logger warning ){
		this.inDir = dir;
		this.fileName = name;
		this.speciesName = species;
		this.mapReader = reader;
		this.outDir = out;
		this.warning = warning;
	}
	@Override
	public RMA6BlastCrawler call() throws Exception {
		RMA6BlastCrawler crawler = new RMA6BlastCrawler(inDir,fileName,speciesName,
				 outDir,mapReader, warning);
		  crawler.process();
		return crawler;
	}

}