package com.hlb.dblogging.web.beans;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;

import javax.faces.application.FacesMessage;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hlb.dblogging.app.context.FacesUtil;
import com.hlb.dblogging.jpa.model.Users;
import com.hlb.dblogging.log.utility.ApplLogger;
import com.hlb.dblogging.security.users.service.UsersService;
import com.hlb.dblogging.user.accesslog.logging.AccessLogActivity;
import com.hlb.dblogging.user.accesslog.logging.AccessLogLevel;
import com.hlb.dblogging.user.accesslog.logging.AccessLogService;
import com.hlb.dblogging.user.audit.logging.AuditTrail;
import com.hlb.dblogging.user.audit.logging.SystemAuditTrailActivity;
import com.hlb.dblogging.user.audit.logging.SystemAuditTrailLevel;

@Component(value="loginAuthenticationBean")
@SessionScoped
@Scope("session")
public class LoginAuthenticationBean implements Serializable {

	private static final long serialVersionUID = 1L;

	@Autowired
	private UsersService usersService;
	private HashSet<String> accessRightsSet = new HashSet<String>();
	private String username;
	private String password;
	private Boolean wrongPassword;
	private Boolean userNotExisted;
	
	private Users actorUsers;
	private String oldPassword;
	private String newPassword;
	
	private Boolean normalUser=Boolean.FALSE;
	
	@Autowired
	private AccessLogService accessLogService;
	
	@Autowired
	private AuditTrail auditTrail;
	
	
	private String sessionTimeout;


	public String getSessionTimeout() {
	   return sessionTimeout;
	}
	public void setSessionTimeout(String sessionTimeout) {
	   this.sessionTimeout = sessionTimeout;
	}

	
	
	
	public Users getActorUsers() {
		return actorUsers;
	}
	public void setActorUsers(Users actorUsers) {
		this.actorUsers = actorUsers;
	}
	
	public LoginAuthenticationBean() {
		ApplLogger.getLogger().info("@@@@@@@@@@@ LoginAuthenticationBean object created... @@@@@@@@@");
	}
	
	
	public Boolean getUserNotExisted() {
		return userNotExisted;
	}
	public void setUserNotExisted(Boolean userNotExisted) {
		this.userNotExisted = userNotExisted;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	public Boolean getNormalUser() {
		return normalUser;
	}
	public void setNormalUser(Boolean normalUser) {
		this.normalUser = normalUser;
	}
	public Boolean getWrongPassword() {
		return wrongPassword;
	}
	public void setWrongPassword(Boolean wrongPassword) {
		this.wrongPassword = wrongPassword;
	}
	
	public String getNewPassword() {
		return newPassword;
	}
	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
	
	public UsersService getUsersService() {
		return usersService;
	}
	public void setUsersService(UsersService usersService) {
		this.usersService = usersService;
	}
	public AuditTrail getAuditTrail() {
		return auditTrail;
	}
	public void setAuditTrail(AuditTrail auditTrail) {
		this.auditTrail = auditTrail;
	}
	
	public String getOldPassword() {
		return oldPassword;
	}
	public void setOldPassword(String oldPassword) {
		this.oldPassword = oldPassword;
	}
	
	// Configured for  Access Logs
	
	public AccessLogService getAccessLogService() {
		return accessLogService;
	}
	public void setAccessLogService(AccessLogService accessLogService) {
		this.accessLogService = accessLogService;
	}
	
	
	public String doLogin() throws ServletException, IOException{
		
		ApplLogger.getLogger().info("Authenticate here...!!!");
		//ApplLogger.getLogger().info("Given username : "+username+" and password : "+password);
		// TODO: Write the code to check user in database and then get list of Authorities.
		if(!checkUserExistInDatabase()){
			ApplLogger.getLogger().info("username not found in the application database : "+username);
			userNotExisted = Boolean.TRUE;
			wrongPassword=Boolean.FALSE;
			return "/login.jsf?faces-redirect=true";
		}
		populateAccessRightsSet();
		
		if(!"admin".equalsIgnoreCase(username)){
		ApplLogger.getLogger().info("Logged in user is Authenticating .......");
		ExternalContext context = FacesContext.getCurrentInstance().getExternalContext();
		RequestDispatcher requestDispatcher = ((ServletRequest) context.getRequest()).getRequestDispatcher("/j_spring_security_check");
		requestDispatcher.forward((ServletRequest) context.getRequest(), (ServletResponse) context.getResponse());
		Authentication auth=SecurityContextHolder.getContext().getAuthentication();
		if(auth!=null){
			ApplLogger.getLogger().info("Authentication is successful..");
			wrongPassword=Boolean.FALSE;
			initUsers();
			accessLogService.log(AccessLogActivity.LOGIN, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " has successfully logged in to the system.");
			
			if(accessRightsSet.contains("USER_HOME"))	
				normalUser = Boolean.TRUE;
			
		}else{
			ApplLogger.getLogger().info("Authentication is failed for user :"+username);
			wrongPassword=Boolean.TRUE;
			userNotExisted = Boolean.FALSE;
			accessLogService.log(AccessLogActivity.FAILED, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " Authentication has been failed .");
			return null;
		}
		}else{
			
			// TODO: Write code for checking password and if wrong, return to login page else get list of Authorities.
			initUsers();
			System.out.println("Password from the database user is : "+actorUsers.getPassword());
			
			PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
			if(passwordEncoder.matches(password,actorUsers.getPassword())){
				userNotExisted = Boolean.FALSE;
				System.out.println("Admin user is authenticated successfully... ");
				accessLogService.log(AccessLogActivity.LOGIN, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " has successfully logged in to the system.");
			}
			else{
				wrongPassword=Boolean.TRUE;
				userNotExisted = Boolean.FALSE;
				accessLogService.log(AccessLogActivity.FAILED, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " Authentication has been failed.");
				return "/login.jsf?faces-redirect=true";
				
			}
			ApplLogger.getLogger().info("Logged in user is Admin...");
		
			HttpSession session = (HttpSession) FacesContext.getCurrentInstance().getExternalContext().getSession(true);
		      
		       int sessiontimeout = session.getMaxInactiveInterval();
		       
		       ApplLogger.getLogger().info("sessiontimeout  time checking . :"+sessiontimeout); 
		       
		       setSessionTimeout("120000");
		       
		       ApplLogger.getLogger().info("sessiontimeout for Admin User..."+sessiontimeout);
   
			return "/pages/admin/adminhomepage.jsf?faces-redirect=true";
		}
		
		
		FacesContext.getCurrentInstance().responseComplete();
		return null;
		
	}
	
	
	
	public void loadUserIntoSession()
	{
		ApplLogger.getLogger().info("Authenticate loadUserIntoSession  here...!!!");
		Authentication auth=SecurityContextHolder.getContext().getAuthentication();
		 Users loggedInUser = (Users) FacesUtil.getSessionMapValue("LOGGEDIN_USER");
		if(loggedInUser == null) {
			//setUsername(auth.getName());
			setUsername(username);
			initUsers();
			populateAccessRightsSet();
			
		/*	accessLogService.log(AccessLogActivity.LOGIN, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " has successfully logged in to the system.");
		//	auditTrail.log(SystemAuditTrailActivity.LOGIN, SystemAuditTrailLevel.INFO, getActorUsers().getId(), getUsername(), getUsername() + " has successfully logged in to the system.");
			*/
		
		} 
	}
	
	
	private boolean checkUserExistInDatabase() {
		try{
		return usersService.findUserExistInApplication(username);
		}catch(Exception e){
			ApplLogger.getLogger().error("Caught Database Exception while checking username in Database for user :"+username,e);
		}
		return false;
	}
	public void doLogout() throws IOException{
		//TODO Write logic to log out 
		
		// Configured for  Access Logs
		
		accessLogService.log(AccessLogActivity.LOGOUT, AccessLogLevel.INFO, actorUsers.getId(), getUsername(), getUsername() + " has successfully logged out of the system.");
		
		SecurityContextHolder.clearContext();
		FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
		    
		ApplLogger.getLogger().info("Logged out successsfuly...!");
		username=null;
		password=null;
		wrongPassword=false;
		FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/login.jsf");
	 }
	
	private void populateAccessRightsSet() {
		try {
			accessRightsSet = usersService.getAccessRightsMapForUser(username);
		} catch(Exception e) {
			ApplLogger.getLogger().error("Error while populating access rights for user :"+username,e);
		}
	}
	
	public Boolean hasAccess(String key) {
		if (key != null && accessRightsSet.contains(key)) {
				return true;
		}		
		return false;
	}
	
	public void hasPageAccess(String key) {
		if (key != null && !accessRightsSet.contains(key)) {
				try {
					FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/accessdenied.jsf");
				} catch(IOException e) {
					e.printStackTrace();
				}
		}		
	}
	
	
	
	public void doUpdatePassword() {
		try {
			actorUsers = usersService.changePassword(actorUsers, oldPassword, getNewPassword());
			auditTrail.log(SystemAuditTrailActivity.UPDATED, SystemAuditTrailLevel.INFO, getActorUsers().getId(), getActorUsers().getUsername(), getActorUsers().getUsername() + " has updated his/her password");
			FacesMessage msg = new FacesMessage("Info : New password saved.");  
			msg.setSeverity(FacesMessage.SEVERITY_INFO);
	        FacesContext.getCurrentInstance().addMessage(null, msg);  
		} catch(Exception e) {
			FacesMessage msg = new FacesMessage("Error : "+ e.getLocalizedMessage());  
			msg.setSeverity(FacesMessage.SEVERITY_ERROR);
	        FacesContext.getCurrentInstance().addMessage(null, msg);  
	        auditTrail.log(SystemAuditTrailActivity.UPDATED, SystemAuditTrailLevel.ERROR, getActorUsers().getId(), getActorUsers().getUsername(), getActorUsers().getUsername() + " tried to update his/her password but failed due to '" + e.getMessage() + "'");
		}
	}
	
	private void initUsers() {
		try {
			actorUsers = getUsersService().findByUsername(getUsername());
			FacesUtil.setSessionMapValue("LOGGEDIN_USER", actorUsers);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	public void keepSessionAlive(){
		FacesContext fc = FacesContext.getCurrentInstance();
		HttpServletRequest request = (HttpServletRequest) fc.getExternalContext().getRequest();
		request.getSession();
	}
	
}