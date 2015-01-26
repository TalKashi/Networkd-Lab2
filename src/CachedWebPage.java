import java.util.Date;


public class CachedWebPage {
	
	Date lastEntranceTime;
	String DomainName;
	String content = "";
	
	public CachedWebPage(String DomainName) {
		this.DomainName = DomainName;
		lastEntranceTime = new Date();
		
	}
	
	public String getDomainName(){
		return this.DomainName;
	}
	public Date getLastEntranceTime (){
		return this.lastEntranceTime;
	}
	public void setLastEntranceTime(Date lastEntranceTime){
		this.lastEntranceTime = lastEntranceTime;
	}
	public String getContent(){
		return this.content;
	}
	public void addLine(String line){
		content += line;
	}

}
