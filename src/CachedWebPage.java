import java.util.Date;


public class CachedWebPage {
	Date lastUsed;
	Date lastUpdate;
	String DomainName;
	
	public CachedWebPage(String DomainName) {
		this.DomainName = DomainName;
		lastUpdate = new Date();
		lastUsed = new Date();
	}
	
	public String getDomainName(){
		return this.DomainName;
	}
	
	public Date getLastUpdate (){
		return this.lastUpdate;
	}
	
	public Date getLastUsedDate (){
		return this.lastUsed;
	}
	
	public void updateLastUsed(){
		this.lastUsed = new Date();
	}
}
