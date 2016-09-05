package RMA6Processor;
//import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import NCBI_MapReader.NCBI_MapReader;
import NCBI_MapReader.NCBI_TreeReader;
import RMA6TaxonProcessor.RMA6TaxonDamageFilter;
import RMA6TaxonProcessor.RMA6TaxonNonDuplicateFilter;
import RMA6TaxonProcessor.RMA6TaxonProcessor;
import behaviour.Filter;
import behaviour.Taxas;
import jloda.util.ListOfLongs;
import megan.rma6.ClassificationBlockRMA6;
import megan.rma6.RMA6File;
/**
 * take care of extracting all the information for one RMA6 file and a List of taxons
 * contains functions to write its own output for supplementary information
 * uses several taxons processors to process RMAFiles in different ways to reflect 
 * the user filter choice and make adding new filters more accessible 
 * @author huebler
 *
 */
public class RMA6Processor {
	// attributes
	private HashMap<Integer,Integer> overallSum;
	private ArrayList<String> readDist;
	private ArrayList<String> supplement;
	private String outDir;
	private String fileName;
	private String inDir;
	private NCBI_MapReader mapReader;
	private NCBI_TreeReader treeReader;
	private Set<Integer> containedIDs;
	private Filter behave;
	private int maxLength;
	private Taxas taxas;
	// constructor
	public RMA6Processor(String inDir, String fileName, String outDir, NCBI_MapReader mapReader, NCBI_TreeReader treeReader, int maxLength, Filter b, Taxas t) {
		this.inDir = inDir;
		this.outDir = outDir;
		this.fileName = fileName;
		this.mapReader = mapReader;
		this.treeReader = new NCBI_TreeReader(treeReader);
		this.behave = b;
		this.maxLength = maxLength;
		this.taxas = t;
	}
	
	//setters
	private Set<Integer> getAllKeys(){
		Set<Integer> keys = null;
		try{
				
			RMA6File rma6File = new RMA6File(inDir+fileName, "r");
			Long location = rma6File.getFooterSectionRMA6().getStartClassification("Taxonomy");
		    if (location != null) {
		        ClassificationBlockRMA6 classificationBlockRMA6 = new ClassificationBlockRMA6("Taxonomy");
		        classificationBlockRMA6.read(location, rma6File.getReader());
		        keys = classificationBlockRMA6.getKeySet();// get all assigned IDs in a file 
		    	}
		    rma6File.close();
		    }catch(IOException io){
				io.printStackTrace();
			}
		return keys;
	}
	private ListOfLongs getLongs(Integer id){
		ListOfLongs list = new ListOfLongs();
		try{
			RMA6File rma6File = new RMA6File(inDir+fileName, "r");
			Long location = rma6File.getFooterSectionRMA6().getStartClassification("Taxonomy");
			if (location != null) {
		        ClassificationBlockRMA6 classificationBlockRMA6 = new ClassificationBlockRMA6("Taxonomy");
		        classificationBlockRMA6.read(location, rma6File.getReader());
		        if (classificationBlockRMA6.getSum(id) > 0) {
					classificationBlockRMA6.readLocations(location, rma6File.getReader(), id, list);
		        }
		    }
			rma6File.close();
		}catch(IOException io){
			io.printStackTrace();
		}	
			return list;
	}
	
	private void setContainedIDs(Set<Integer> set){
		this.containedIDs = set;
	}
	private void setSumLine(HashMap<Integer,Integer> list)
	{	this.overallSum = list;
	}
	private void setSupplementaryData(ArrayList<String> s){
		String header = "ReadName\tMatchLength\tPercentIdent\tNumberOfMatches\tConsideredMatches\tNumberOfEndDamagedMatches\tGC_Content\tTaxName";
		s.add(0, header);
		this.supplement = s ;
	}
	
	private void setReadDistribution(ArrayList<String>r){
		String header = "Taxon\tReference\tMeanReadDistance\tMedianReadDistance\tVarianceReadDistance\tStandardDeviationReadDistance\tuniquePerReference\tnonDuplicatesonReference\tTotalReadsOnReference\tReferenceLength";
		r.add(0,header);
		this.readDist = r;
	}
	// private utility functions
	private void writeReadDist(List<String> summary, String fileName){
		try{
			//System.out.println("Writing Read Distribution txt File");
			Path file = Paths.get(outDir+fileName+"_readDist"+".txt");
			Files.write(file, summary, Charset.forName("UTF-8"));
			//System.out.println("ReadDistribution for " + fileName +" Done!");
		}catch(IOException io){
			io.printStackTrace();
		}
		this.readDist = null; //delete data to save space after were done potentially the gc should take of it
	}
	
	private void writeSupplementary(List<String> supplement, String fileName){ 
		try{
		OutputStreamWriter outer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outDir+fileName+"_supplement"+ ".txt.gz")));
		for(String line : supplement){
			outer.write(line+"\n");
		}
		outer.close();
		//System.out.println("Supplementary for File "+fileName+" done!");
		}catch(IOException io){
			io.printStackTrace();
		}
		this.supplement = null; //delete data to save space after were done
	}
	
	//getter
	public ArrayList<String> getReadDistribution(){
		return this.readDist;
	}

	public ArrayList<String> getSupplementary(){
		return this.supplement;
	}
	public HashMap<Integer,Integer> getSumLine(){
		return this.overallSum;
	}
	public Set<Integer> getContainedIDs(){
		return this.containedIDs;
	}
	public String getfileName(){
		return this.fileName;
	}
// processing 
public void process(List<Integer>taxIDs, double topPercent) {
	System.out.println("Reading File: " +inDir+fileName);
	HashMap<Integer,Integer> overallSum = new HashMap<Integer,Integer>();
	ArrayList<String> supplemantary = new ArrayList<String>();
	ArrayList<String> readDistribution = new ArrayList<String>();
	Set<Integer> keys = getAllKeys();
	Set<Integer> idsToProcess = new HashSet<Integer>();
   // treeReader here to avoid synchronization issues 
	if(taxas == Taxas.USER){
		for(Integer taxID : taxIDs){
			idsToProcess.add(taxID);
			for(Integer id : treeReader.getStrains(taxID, keys))
				if(!taxIDs.contains(id))
					idsToProcess.add(id);
		}
	}
	else if(taxas == Taxas.ALL){
		idsToProcess.addAll(keys);
	}
	setContainedIDs(idsToProcess);
	for(Integer id : idsToProcess){
		ListOfLongs list = getLongs(id);
		if(behave == Filter.NON){
			
			RMA6TaxonProcessor taxProcessor = new RMA6TaxonProcessor(id,mapReader,list);
			taxProcessor.process(inDir,  fileName,  topPercent,maxLength);
			overallSum.put(id,taxProcessor.getNumberOfMatches());
			readDistribution.add(taxProcessor.getReadDistribution());
			supplemantary.addAll(taxProcessor.getSupplementary());
		

		}else if(behave == Filter.ANCIENT){
			
			RMA6TaxonDamageFilter damageProcessor = new RMA6TaxonDamageFilter(id,mapReader, list);
			damageProcessor.process(inDir, fileName,  topPercent, maxLength);
			overallSum.put(id,damageProcessor.getNumberOfMatches());
			readDistribution.add(damageProcessor.getReadDistribution());
			supplemantary.addAll(damageProcessor.getSupplementary());
		
		}else if(behave == Filter.NONDUPLICATES){
			RMA6TaxonNonDuplicateFilter nonDP = new RMA6TaxonNonDuplicateFilter(id,mapReader, list);
			nonDP.process(inDir,  fileName, topPercent, maxLength);
			overallSum.put(id,nonDP.getNumberOfMatches());
			readDistribution.add(nonDP.getReadDistribution());
			supplemantary.addAll(nonDP.getSupplementary());
		}
		
	  }//TaxIDs
	setSupplementaryData(supplemantary);	//save supplementary data at read resolution in adequate slot
	setSumLine(overallSum); // set number of assigned Reads to overall file summary
	setReadDistribution(readDistribution);// save ReadDist summary file
	writeReadDist(getReadDistribution(),fileName); // RMA6Processor now saves its own output 
	writeSupplementary(getSupplementary(),fileName);
    }
 }
