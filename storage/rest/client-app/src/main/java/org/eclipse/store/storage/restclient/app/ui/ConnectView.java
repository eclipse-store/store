
package org.eclipse.store.storage.restclient.app.ui;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.store.storage.restclient.app.types.SessionData;
import org.eclipse.store.storage.restclient.jersey.types.StorageRestClientJersey;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Route(value = "", layout = RootLayout.class)
public class ConnectView extends VerticalLayout implements HasDynamicTitle
{
	public ConnectView()
	{
		super();
		
		final ComboBox<String> urlChooser = new ComboBox<>();
		urlChooser.setId(ElementIds.COMBO_URL);
		urlChooser.setMinWidth("50ch");
		urlChooser.setItems(DataProvider.ofCollection(this.urls()));
		urlChooser.setAllowCustomValue(true);
		urlChooser.addCustomValueSetListener(event -> urlChooser.setValue(event.getDetail()));
		
		final Button cmdConnect = new Button(this.getTranslation("CONNECT"),
			event -> {
				String url = urlChooser.getValue();
				if(url != null && !(url = url.trim()).isEmpty())
				{
					this.tryConnect(url);
				}
				// re-enable button because disableOnClick=true
				event.getSource().setEnabled(true);
			}
		);
		cmdConnect.setId(ElementIds.BUTTON_CONNECT);
		cmdConnect.setIcon(new Image(UIUtils.imagePath("login.svg"), ""));
		cmdConnect.setDisableOnClick(true);
		
		final HorizontalLayout connectLayout = new HorizontalLayout(
			new NativeLabel(this.getTranslation("URL") + ":"),
			urlChooser,
			cmdConnect
		);
		connectLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		
		final VerticalLayout connectFrame = new VerticalLayout(
			new H3(this.getTranslation("CONNECT_HEADER")),
			connectLayout
		);
		connectFrame.setMargin(true);
		connectFrame.addClassName(ClassNames.BOX);
		connectFrame.setSizeUndefined();
		
		this.setHorizontalComponentAlignment(Alignment.CENTER, connectFrame);
		this.add(connectFrame);
		this.addClassName(ClassNames.BACKGROUND_THEME);
		this.setSizeFull();
	}
	
	@Override
	public String getPageTitle()
	{
		return this.getTranslation("CONNECT") + " - " + RootLayout.PAGE_TITLE;
	}
	
	private void tryConnect(
		final String baseUrl
	)
	{
		try(final StorageRestClientJersey client = StorageRestClientJersey.New(baseUrl))
		{
			client.requestRoot();
			
			this.updateUrlCookie(baseUrl);
			
			final SessionData sessionData = new SessionData(baseUrl);
			this.getUI().ifPresent(ui -> {
				ui.getSession().setAttribute(SessionData.class, sessionData);
				ui.navigate(InstanceView.class);
			});
		}
		catch(final Exception e)
		{
			this.getUI().ifPresent(ui -> {

				final Notification notification = new Notification();
				
				final H3 header = new H3(this.getTranslation("CONNECT_ERROR"));
				header.addClassName(ClassNames.ERROR);
				
				final Button close = new Button(
					this.getTranslation("OK"),
					event -> notification.close()
				);
				
				final VerticalLayout content = new VerticalLayout(
					header,
					new Hr(),
					new NativeLabel(this.getTranslation("INTERNAL_ERROR_HINT", baseUrl)),
					close
				);
				content.setHorizontalComponentAlignment(Alignment.END, close);
				
				notification.add(content);
				notification.setDuration(0);
				notification.setPosition(Position.MIDDLE);
				notification.open();
			});
		}
	}
	
	private Set<String> urls()
	{
		final Set<String> urlSelection = new LinkedHashSet<>();
		urlSelection.add("http://localhost:8080/store-data/default/");
		urlSelection.add("http://localhost:4567/store-data/");

		final HttpServletRequest request = (HttpServletRequest) VaadinRequest.getCurrent();
		final Cookie[]           cookies = request.getCookies();
		if(cookies != null)
		{
			final Cookie urlCookie = Arrays.stream(cookies)
				.filter(c -> c.getName().equals("URL"))
				.findAny()
				.orElse(null);
			if(urlCookie != null)
			{
				final String urls = new String(
					Base64.getUrlDecoder().decode(urlCookie.getValue()),
					StandardCharsets.UTF_8
				);
        urlSelection.addAll(Arrays.asList(urls.split("\n")));
			}
		}
		return urlSelection;
	}
	
	private void updateUrlCookie(
		final String url
	)
	{
		final Set<String> urls = this.urls();
		if(urls.add(url))
		{
			final String cookieData = String.join("\n", urls);
			
			final HttpServletResponse response = (HttpServletResponse) VaadinResponse.getCurrent();
			final Cookie cookie = new Cookie(
				"URL",
				new String(
					Base64.getUrlEncoder().encode(
						cookieData.getBytes(StandardCharsets.UTF_8)
					),
					StandardCharsets.UTF_8
				)
			);
			response.addCookie(cookie);
		}
	}
	
}
