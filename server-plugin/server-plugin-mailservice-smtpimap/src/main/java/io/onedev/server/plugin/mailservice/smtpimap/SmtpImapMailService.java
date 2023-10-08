package io.onedev.server.plugin.mailservice.smtpimap;

import io.onedev.server.OneDev;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Password;
import io.onedev.server.mail.*;
import io.onedev.server.model.support.administration.mailservice.MailService;
import io.onedev.server.model.support.administration.mailservice.SmtpExplicitSsl;
import io.onedev.server.model.support.administration.mailservice.SmtpSslSetting;
import org.jetbrains.annotations.Nullable;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Collection;

@Editable(name="SMTP/IMAP", order=100)
public class SmtpImapMailService implements MailService {

	private static final long serialVersionUID = 1L;
	
	private String systemAddress;
	
	private String smtpHost;

	private SmtpSslSetting sslSetting = new SmtpExplicitSsl();

	private String smtpUser;

	private String smtpPassword;

	private InboxPollSetting inboxPollSetting;

	private int timeout = 60;
	
	private transient MailPosition mailPosition;

	@Editable(order=100, name="SMTP Host")
	@NotEmpty
	public String getSmtpHost() {
		return smtpHost;
	}

	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}

	@Editable(order=200, name="SSL Setting")
	@NotNull
	public SmtpSslSetting getSslSetting() {
		return sslSetting;
	}

	public void setSslSetting(SmtpSslSetting sslSetting) {
		this.sslSetting = sslSetting;
	}

	@Editable(order=300, name="SMTP User")
	public String getSmtpUser() {
		return smtpUser;
	}

	public void setSmtpUser(String smtpUser) {
		this.smtpUser = smtpUser;
	}

	@Editable(order=400, name="SMTP Password")
	@Password(autoComplete="new-password")
	public String getSmtpPassword() {
		return smtpPassword;
	}

	public void setSmtpPassword(String smtpPassword) {
		this.smtpPassword = smtpPassword;
	}

	@Editable(order=425, name="System Email Address", description="This address will be used as sender address of "
			+ "various notifications. Emails targeting this address and its sub addressing in the IMAP inbox will "
			+ "also be checked if <code>Check Incoming Email</code> option is enabled below")
	@Email
	@NotEmpty
	@Override
	public String getSystemAddress() {
		return systemAddress;
	}

	public void setSystemAddress(String systemAddress) {
		this.systemAddress = systemAddress;
	}

	@Editable(order=450, name="Check Incoming Email", description="Enable this to post issue and pull request comments via email. "
			+ "<b class='text-danger'>NOTE:</b> <a href='https://en.wikipedia.org/wiki/Email_address#Subaddressing' target='_blank'>Sub addressing</a> "
			+ "needs to be enabled for above email address, as OneDev uses it to track issue and pull request contexts")
	public InboxPollSetting getInboxPollSetting() {
		return inboxPollSetting;
	}

	public void setInboxPollSetting(InboxPollSetting inboxPollSetting) {
		this.inboxPollSetting = inboxPollSetting;
	}

	@Editable(order=10000, description="Specify timeout in seconds when communicating with mail server")
	@Min(value=5, message="This value should not be less than 5")
	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	private SmtpSetting getSmtpSetting() {
		MailCredential smtpCredential;
		if (smtpPassword != null)
			smtpCredential = new BasicAuthPassword(smtpPassword);
		else
			smtpCredential = null;
		return new SmtpSetting(smtpHost, sslSetting, smtpUser, smtpCredential, getTimeout());
	}

	@Nullable
	private ImapSetting getImapSetting() {
		if (inboxPollSetting != null) {
			String imapUser = inboxPollSetting.getImapUser();
			MailCredential imapCredential = new BasicAuthPassword(inboxPollSetting.getImapPassword());
			return new ImapSetting(inboxPollSetting.getImapHost(), inboxPollSetting.getSslSetting(),
					imapUser, imapCredential, inboxPollSetting.getPollInterval(), getTimeout());
		} else {
			return null;
		}
	}
	
	@Override
	public void sendMail(Collection<String> toList, Collection<String> ccList, Collection<String> bccList, 
						 String subject, String htmlBody, String textBody, @Nullable String replyAddress, 
						 @Nullable String senderName, @Nullable String references) {
		getMailManager().sendMail(getSmtpSetting(), toList, ccList, bccList, subject, htmlBody, textBody, 
				replyAddress, senderName, getSystemAddress(), references);
	}

	@Override
	public InboxMonitor getInboxMonitor() {
		var imapSetting = getImapSetting();
		if (imapSetting != null) {
			return (messageConsumer, testMode) -> {
				if (mailPosition == null)
					mailPosition = new MailPosition();
				return getMailManager().monitorInbox(getImapSetting(), getSystemAddress(), 
						messageConsumer, mailPosition, testMode);
			};
		} else {
			return null;			
		}
	}

	private MailManager getMailManager() {
		return OneDev.getInstance(MailManager.class);
	}
	
}
