package RMA6Processor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import NCBI_MapReader.NCBI_MapReader;
import NCBI_MapReader.NCBI_TreeReader;
import RMAAlignment.Alignment;
import jloda.util.ListOfLongs;
import megan.data.IMatchBlock;
import megan.data.IReadBlock;
import megan.data.IReadBlockIterator;
import megan.data.ReadBlockIterator;
import megan.rma6.ClassificationBlockRMA6;
import megan.rma6.RMA6File;
import megan.rma6.ReadBlockGetterRMA6;
import strainMap.StrainMap;
import strainMap.StrainMisMatchContainer;

public class RMA6BlastCrawler {
	/**
	 * Go through whole file and find blast hits that that match the strains of the target species
	 */
	
	//TODO what am I missing here 
	private String inDir;
	private String fileName;
	private String speciesName;
	private NCBI_MapReader mapReader;
	private String outDir;
	private Logger warning;
	private ArrayList<String> summary= new ArrayList<String>();
	private NCBI_TreeReader treeReader;
	public RMA6BlastCrawler(String dir, String name, String species, String out, NCBI_MapReader reader ,Logger warning,NCBI_TreeReader treeReader){
		this.inDir = dir;
		this.fileName = name;
		this.speciesName = species;
		this.mapReader = reader;
		this.outDir = out;
		this.warning = warning;
		this.treeReader = new NCBI_TreeReader(treeReader);
	}
	private String getName(int taxId){
		String name;
		if(mapReader.getNcbiIdToNameMap().get(taxId) != null)
			name = mapReader.getNcbiIdToNameMap().get(taxId);
		else
			name = "unassignedName";
		return name;
	}
	private void writeMisMap(ArrayList<String> summary){
		String header = "Node";
		String header_part2 ="";
		for(int i = 0; i < 20; i++){
			if(i<10){
				header+="\t"+"C>T_"+(i+1);
				header_part2+="\t"+"D>V(11Substitution)_"+(i+1);
			}else{
				header+="\t"+"G>A_"+(i+1);
				header_part2+="\t"+"H>B(11Substitution)_"+(i+1);
			}
		}
		header+=header_part2+"\tconsidered_Matches";
		summary.sort(null);
		summary.add(0,header);
	try{
		Path file = Paths.get(outDir+"/crawlResults/"+fileName+"_"+speciesName.replace(' ', '_')+"_misMatch.txt");
		Files.write(file, summary, Charset.forName("UTF-8"));
	}catch(IOException io){
		warning.log(Level.SEVERE,"Can't write File" ,io);
	}
 
}
	private Set<Integer> getAllKeys(String fileName){
		Set<Integer> keys = null;
		try(RMA6File rma6File = new RMA6File(fileName, "r")){
			Long location = rma6File.getFooterSectionRMA6().getStartClassification("Taxonomy");
		    if (location != null) {
		        ClassificationBlockRMA6 classificationBlockRMA6 = new ClassificationBlockRMA6("Taxonomy");
		        classificationBlockRMA6.read(location, rma6File.getReader());
		        keys = classificationBlockRMA6.getKeySet();// get all assigned IDs in a file 
		    }
		    rma6File.close();
		    }catch(IOException io){
		    	warning.log(Level.SEVERE,"Can't write File" ,io);io.printStackTrace();
			}
		
		return keys;
	}
	public void process(){
		HashMap<Integer, StrainMap> collection = new HashMap<Integer, StrainMap>();
		HashSet<Integer> idsToProcess = new HashSet<Integer>();
		int taxID = mapReader.getNcbiNameToIdMap().get(speciesName);
		idsToProcess.add(taxID);
		for(Integer id : treeReader.getAllStrains(taxID, getAllKeys(inDir+fileName)))
				idsToProcess.add(id);
		idsToProcess.addAll(treeReader.getParents(taxID));
		for(Integer id : idsToProcess){
			try(RMA6File rma6File = new RMA6File(inDir+fileName, "r")){
				ListOfLongs list = new ListOfLongs();
				Long location = rma6File.getFooterSectionRMA6().getStartClassification("Taxonomy");
				if (location != null) {
				   ClassificationBlockRMA6 classificationBlockRMA6 = new ClassificationBlockRMA6("Taxonomy");
				   classificationBlockRMA6.read(location, rma6File.getReader());
				   if (classificationBlockRMA6.getSum(id) > 0) {
					   classificationBlockRMA6.readLocations(location, rma6File.getReader(), id, list);
				   }
				 }
				IReadBlockIterator classIt  = new ReadBlockIterator(list, new ReadBlockGetterRMA6(rma6File, true, true, (float) 1.0,(float) 100.00,false,true));
				// get all stuff
				while(classIt.hasNext()){
					IReadBlock current = classIt.next();
							IMatchBlock[] blocks = current.getMatchBlocks();
							float topScore = current.getMatchBlock(0).getBitScore();
							for(int i = 0; i< blocks.length;i++){
								if(blocks[i].getBitScore()/topScore < 1-0.01){
									break;}
								if(getName(blocks[i].getTaxonId()).contains(speciesName)){
				
									Alignment al = new Alignment();
									al.processText(blocks[i].getText().split("\n"));
									if(collection.containsKey(blocks[i].getTaxonId())){
										StrainMap strain = collection.get(blocks[i].getTaxonId());
										StrainMisMatchContainer container =	strain.getStrainMisMatchContainer();
										container.processAlignment(al);
										strain.setStrainMisMatchContainer(container);
										strain.setNumberOfMatches(strain.getNumberOfMatches()+1);
										collection.replace(blocks[i].getTaxonId(), strain);
							
									}else{
										StrainMisMatchContainer container = new StrainMisMatchContainer();
										container.processAlignment(al);
										StrainMap strain = new StrainMap(mapReader.getNcbiIdToNameMap().get(blocks[i].getTaxonId()),
										container,1);
										collection.put(blocks[i].getTaxonId(), strain);
											}//else
										}//if
						}// other for 	//matches
				}	
				classIt.close();
				rma6File.close();
			}catch(IOException io){
				warning.log(Level.SEVERE,"Can not locate or read File" ,io);
			}
		}// for all IDs
		for(int key :collection.keySet()){// write output here 
		summary.add(collection.get(key).getLine());
		}
		
		writeMisMap(summary);
	}
}
