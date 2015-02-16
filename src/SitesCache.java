import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class SitesCache {
	private final int MAX_NUMBER_OF_SITES_IN_CACHE = 20;
	private final int MAX_MINUTES_WITHOUT_UPDATE = 60 * 48;
	private static Map<String , CachedWebPage> sites = new HashMap<String, CachedWebPage>();
	

	public boolean containsAndUpdateTime(String site){
		if(sites.containsKey(site)){
			long lastUpdate = sites.get(site).getLastUpdate().getTime();
			long now = new Date().getTime();
			if((lastUpdate - now)/(1000*60) < MAX_MINUTES_WITHOUT_UPDATE){
				return true;
			}
		}
		return false;
	}
	public void updateLastUsed(String site){
		sites.get(site).updateLastUsed();
	}

	public CachedWebPage getSite(String site){
		if(sites.containsKey(site)){
			return sites.get(site);
		}
		return null;  
	}

	public synchronized void addSite(String firstLine, ProxyHandler proxy) {
		if(!sites.containsKey(firstLine)){
			CachedWebPage webPage = new CachedWebPage(firstLine);
			sites.put(firstLine, webPage);
			if(sites.size() >= MAX_NUMBER_OF_SITES_IN_CACHE){
				removeMostUnusedSite();
			}
		}
	}
	
	private void removeMostUnusedSite() {
		String siteToRemove = getUnusedSiteToRemove();
		try{
			String siteToRemoveFileName = String.valueOf(siteToRemove.hashCode());
			File file = new File("cache/" + siteToRemoveFileName);
			
			if(file.delete()){
				System.out.println(file.getName() + " is deleted!");
			}else{
				System.out.println("Delete operation failed.");
			}
		}catch(Exception e){
			System.out.println("Delete operation failed.");
		}
		sites.remove(siteToRemove);
	}

	private String getUnusedSiteToRemove(){
		String toRemove = "";
		Date time = new Date();
		for(String site : sites.keySet()){
			if(sites.get(site).getLastUsedDate().before(time)){
				time = sites.get(site).getLastUsedDate();
				toRemove = site;
			}
		}
		return toRemove;
	}
}
