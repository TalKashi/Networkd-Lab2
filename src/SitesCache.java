import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class SitesCache {
	private final int MAX_NUMBER_OF_SITES_IN_CACHE = 20;
	private static Map<String , CachedWebPage> sites = new HashMap<String, CachedWebPage>();

	public boolean isSiteContained(String site){
		if(sites.containsKey(site)){
			return true;
		}
		return false;
	}
	public void updateSiteTime(String site){
			sites.get(site).setLastEntranceTime(new Date());
	}
	
	public CachedWebPage getSite(String site){
		if(isSiteContained(site)){
			return sites.get(site);
		}
		return null;
	}
	
	public synchronized  void addSite(String site){
		if(!isSiteContained(site)){
			CachedWebPage webPage = new CachedWebPage(site);
			sites.put(site, webPage);
			if(sites.size() >= MAX_NUMBER_OF_SITES_IN_CACHE){
				sites.remove(getSiteToRemove());
			}
		}
	}

	private String getSiteToRemove(){
		String toRemove = "";
		Date time = new Date();
		for(String site : sites.keySet()){
			if(sites.get(site).getLastEntranceTime().before(time)){
				time = sites.get(site).getLastEntranceTime();
				toRemove = site;
			}
		}
		return toRemove;
	}

}
