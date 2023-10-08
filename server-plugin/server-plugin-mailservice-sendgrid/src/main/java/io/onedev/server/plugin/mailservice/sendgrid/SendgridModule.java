package io.onedev.server.plugin.mailservice.sendgrid;

import com.google.common.collect.Sets;
import io.onedev.commons.loader.AbstractPluginModule;
import io.onedev.commons.loader.ImplementationProvider;
import io.onedev.server.jetty.ServletConfigurator;
import io.onedev.server.model.support.administration.mailservice.MailService;

import java.util.Collection;

/**
 * NOTE: Do not forget to rename moduleClass property defined in the pom if you've renamed this class.
 *
 */
public class SendgridModule extends AbstractPluginModule {

	@Override
	protected void configure() {
		super.configure();
		
		// put your guice bindings here
		contribute(ImplementationProvider.class, new ImplementationProvider() {

			@Override
			public Class<?> getAbstractClass() {
				return MailService.class;
			}

			@Override
			public Collection<Class<?>> getImplementations() {
				return Sets.newHashSet(SendgridMailService.class);
			}
			
		});
		
		bind(MessageManager.class).to(DefaultMessageManager.class);
		bind(SendgridServlet.class);
		contribute(ServletConfigurator.class, SendgridServletConfigurator.class);
	}

}
