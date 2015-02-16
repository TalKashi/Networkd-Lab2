import java.util.Date;


public class CachedWebPage {
	Date lastUsed;
	Date lastUpdate;
	String firstLine;
	
	public CachedWebPage(String firstLine) {
		this.firstLine = firstLine.toLowerCase();
		lastUpdate = new Date();
		lastUsed = new Date();
	}
	
	public String getDomainName(){
		return this.firstLine;
	}
	
	public Date getLastUpdate (){
		return this.lastUpdate;
	}
	
	public Date getLastUsedDate (){
		return this.lastUsed;
	}
	
	public String getFileName(){
		return String.valueOf(firstLine.hashCode());
	}
	
	public void updateLastUsed(){
		this.lastUsed = new Date();
	}
}
