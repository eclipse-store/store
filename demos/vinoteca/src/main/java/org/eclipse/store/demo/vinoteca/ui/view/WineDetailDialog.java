package org.eclipse.store.demo.vinoteca.ui.view;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.eclipse.store.demo.vinoteca.dto.ReviewInput;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.WineService;

/**
 * Modal dialog showing the full detail of a single {@link Wine}: producer info, type/grape,
 * price, rating, alcohol/stock, tasting notes/aroma/food pairing, an existing-reviews list, and
 * a form to attach a new review.
 * <p>
 * Used as the "drill-down" target for the wine grids on the catalog, full-text-search,
 * similarity-search and analytics views.
 */
public class WineDetailDialog extends Dialog
{
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final Wine            wine;
	private final WineService     wineService;
	private final CustomerService customerService;
	private final VerticalLayout  reviewsContainer;
	private final Span            ratingLabel;
	private final Runnable        onReviewAdded;

	/**
	 * Creates the dialog (but does not open it — call {@link #open()}).
	 *
	 * @param wine            the wine to display
	 * @param wineService     the wine service used to load reviews and submit new ones
	 * @param customerService the customer service used to populate the reviewer drop-down
	 * @param onReviewAdded   optional callback invoked after a new review has been persisted
	 *                        (typically used by the calling view to refresh its grid row); may be
	 *                        {@code null}
	 */
	public WineDetailDialog(
		final Wine            wine,
		final WineService     wineService,
		final CustomerService customerService,
		final Runnable        onReviewAdded
	)
	{
		this.wine             = wine;
		this.wineService      = wineService;
		this.customerService  = customerService;
		this.reviewsContainer = new VerticalLayout();
		this.ratingLabel      = new Span();
		this.onReviewAdded    = onReviewAdded;

		setHeaderTitle(wine.getName() + " (" + wine.getVintage() + ")");
		setWidth("800px");
		setMaxHeight("90vh");

		final VerticalLayout content = new VerticalLayout();
		content.setPadding(false);
		content.setSpacing(true);

		content.add(this.createDetailsSection());
		content.add(this.createReviewsSection());

		add(content);

		final Button closeBtn = new Button("Close", e -> close());
		closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		getFooter().add(closeBtn);
	}

	private FormLayout createDetailsSection()
	{
		final FormLayout form = new FormLayout();
		form.setResponsiveSteps(
			new FormLayout.ResponsiveStep("0", 1),
			new FormLayout.ResponsiveStep("400px", 2),
			new FormLayout.ResponsiveStep("600px", 3)
		);

		form.addFormItem(new Span(this.wine.getWinery() != null ? this.wine.getWinery().getName() : "-"), "Winery");
		form.addFormItem(new Span(this.wine.getWinery() != null ? this.wine.getWinery().getRegion() : "-"), "Region");
		form.addFormItem(new Span(this.wine.getWinery() != null ? this.wine.getWinery().getCountry() : "-"), "Country");
		form.addFormItem(new Span(this.wine.getType().name()), "Type");
		form.addFormItem(new Span(this.wine.getGrapeVariety().name().replace('_', ' ')), "Grape Variety");
		form.addFormItem(new Span(String.format("%.2f EUR", this.wine.getPriceAsDouble())), "Price");

		this.updateRatingLabel();
		form.addFormItem(this.ratingLabel, "Rating");

		form.addFormItem(new Span(String.format("%.1f%%", this.wine.getAlcoholContent())), "Alcohol");
		form.addFormItem(new Span(String.valueOf(this.wine.getBottlesInStock())), "Stock");
		form.addFormItem(new Span(this.wine.isAvailable() ? "Yes" : "No"), "Available");

		if (this.wine.getTastingNotes() != null && !this.wine.getTastingNotes().isBlank())
		{
			final Span notes = new Span(this.wine.getTastingNotes());
			form.addFormItem(notes, "Tasting Notes");
			form.setColspan(form.getChildren()
				.reduce((a, b) -> b).orElse(null), 3);
		}
		if (this.wine.getAroma() != null && !this.wine.getAroma().isBlank())
		{
			final Span aroma = new Span(this.wine.getAroma());
			form.addFormItem(aroma, "Aroma");
			form.setColspan(form.getChildren()
				.reduce((a, b) -> b).orElse(null), 3);
		}
		if (this.wine.getFoodPairing() != null && !this.wine.getFoodPairing().isBlank())
		{
			final Span pairing = new Span(this.wine.getFoodPairing());
			form.addFormItem(pairing, "Food Pairing");
			form.setColspan(form.getChildren()
				.reduce((a, b) -> b).orElse(null), 3);
		}

		return form;
	}

	private VerticalLayout createReviewsSection()
	{
		final VerticalLayout section = new VerticalLayout();
		section.setPadding(false);
		section.setSpacing(true);

		section.add(new H4("Reviews"));

		this.reviewsContainer.setPadding(false);
		this.reviewsContainer.setSpacing(false);
		this.loadReviews();

		final Scroller scroller = new Scroller(this.reviewsContainer);
		scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
		scroller.setMaxHeight("350px");
		scroller.setWidthFull();

		section.add(scroller);
		section.add(new H4("Add Review"));
		section.add(this.createReviewForm());

		return section;
	}

	private Div createReviewCard(final Review review)
	{
		final Div card = new Div();
		card.addClassNames(
			LumoUtility.Background.CONTRAST_5,
			LumoUtility.BorderRadius.MEDIUM,
			LumoUtility.Padding.MEDIUM,
			LumoUtility.Margin.Bottom.SMALL
		);
		card.setWidthFull();

		final Span customerName = new Span(review.getCustomer().getFullName());
		customerName.addClassNames(LumoUtility.FontWeight.BOLD);

		final Span date = new Span(review.getDate() != null ? review.getDate().format(DATE_FMT) : "");
		date.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

		final HorizontalLayout header = new HorizontalLayout(customerName, date);
		header.setWidthFull();
		header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
		header.setAlignItems(FlexComponent.Alignment.CENTER);
		header.setPadding(false);

		final Span ratingBadge = new Span(String.format("%.0f / 100", review.getRating()));
		ratingBadge.getElement().getThemeList().add("badge");
		if (review.getRating() >= 80)
		{
			ratingBadge.getElement().getThemeList().add("success");
		}
		else if (review.getRating() < 50)
		{
			ratingBadge.getElement().getThemeList().add("error");
		}

		final Paragraph text = new Paragraph(review.getText() != null ? review.getText() : "");
		text.addClassNames(LumoUtility.Margin.Top.XSMALL, LumoUtility.Margin.Bottom.NONE);

		card.add(header, ratingBadge, text);
		return card;
	}

	private FormLayout createReviewForm()
	{
		final List<Customer> customers = this.customerService.list(0, 1000).content();

		final ComboBox<Customer> customerCombo = new ComboBox<>("Customer");
		customerCombo.setItems(customers);
		customerCombo.setItemLabelGenerator(Customer::getFullName);
		customerCombo.setWidthFull();

		final NumberField ratingField = new NumberField("Rating (1\u2013100)");
		ratingField.setMin(1);
		ratingField.setMax(100);
		ratingField.setStep(1);
		ratingField.setValue(80.0);

		final TextArea textArea = new TextArea("Comment");
		textArea.setWidthFull();
		textArea.setMaxLength(500);

		final Button submitBtn = new Button("Submit Review", e -> {
			if (customerCombo.getValue() == null)
			{
				Notification.show("Please select a customer", 3000, Notification.Position.MIDDLE)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
				return;
			}
			if (ratingField.getValue() == null)
			{
				Notification.show("Please enter a rating", 3000, Notification.Position.MIDDLE)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
				return;
			}

			final int customerIndex = customers.indexOf(customerCombo.getValue());
			final ReviewInput input = new ReviewInput(
				customerIndex,
				ratingField.getValue(),
				textArea.getValue()
			);

			this.wineService.addReview(this.wine, input);
			this.loadReviews();
			this.updateRatingLabel();
			if (this.onReviewAdded != null)
			{
				this.onReviewAdded.run();
			}

			textArea.clear();
			ratingField.setValue(80.0);
			customerCombo.clear();
			Notification.show("Review added", 3000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
		});
		submitBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		final FormLayout form = new FormLayout();
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
		form.add(customerCombo, ratingField, textArea, submitBtn);
		form.setColspan(textArea, 2);

		return form;
	}

	private void loadReviews()
	{
		this.reviewsContainer.removeAll();
		final List<Review> reviews = this.wineService.getReviews(this.wine);
		if (reviews.isEmpty())
		{
			final Span empty = new Span("No reviews yet. Be the first to review this wine!");
			empty.addClassNames(LumoUtility.TextColor.SECONDARY);
			this.reviewsContainer.add(empty);
		}
		else
		{
			reviews.forEach(r -> this.reviewsContainer.add(this.createReviewCard(r)));
		}
	}

	private void updateRatingLabel()
	{
		this.ratingLabel.setText(String.format("%.1f (%d reviews)", this.wine.getRating(), this.wine.getRatingCount()));
	}
}
