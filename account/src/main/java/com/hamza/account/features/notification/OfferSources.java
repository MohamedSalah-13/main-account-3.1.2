package com.hamza.account.features.notification;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferAlerts;
import com.hamza.account.features.offers.OfferService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCommand;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The two offer reminders (docs/pricing-and-offers-plan.md phase E). Both are thin: what is worth saying, and
 * about which offer, is {@link OfferAlerts}' and is tested there without a scheduler.
 *
 * <p>Both are silent for an edition without the offers add-on and for a shop with no offer switched on - so
 * upgrading raises nothing - and both are enabled only for a reader who may open the offers screen, which is
 * where their action goes.</p>
 */
public final class OfferSources {

    private OfferSources() {
    }

    /**
     * An offer whose last day is today or tomorrow. One key per offer and end, so the poll folds into one
     * entry, and moving the end - which the offer screen allows even once the offer is used - is what makes
     * it stop.
     */
    public static final class Ending implements NotificationSource {

        public static final String ID = "offers.ending";
        private final NotificationCommand openOffers;

        public Ending(NotificationCommand openOffers) {
            this.openOffers = openOffers;
        }

        @NotNull
        @Override
        public String id() {
            return ID;
        }

        @NotNull
        @Override
        public String category() {
            return NotificationCategories.ITEMS;
        }

        @NotNull
        @Override
        public String displayName() {
            return text("offer.notification.ending.name");
        }

        @NotNull
        @Override
        public Duration interval() {
            return Duration.ofHours(6);
        }

        @Override
        public boolean enabled() {
            return readable();
        }

        @NotNull
        @Override
        public List<AppNotification> poll() throws Exception {
            OfferService service = ServiceRegistry.get(OfferService.class);
            if (service == null || !service.enabled()) {
                return List.of();
            }
            return service.endingSoon(LocalDate.now()).stream()
                    .map(ending -> AppNotification.builder(ID + "." + ending.offer().id() + "." + ending.offer().endsOn())
                            .category(category())
                            .severity(NotificationSeverity.INFO)
                            .title(text(ending.daysLeft() == 0 ? "offer.notification.ending.today.title"
                                    : "offer.notification.ending.tomorrow.title"))
                            .message(LanguageManager.getInstance().getString(ending.daysLeft() == 0
                                            ? "offer.notification.ending.today.message"
                                            : "offer.notification.ending.tomorrow.message",
                                    ending.offer().name()))
                            .payload(ending)
                            .onOpen(text("offers.title"), openOffers)
                            .build())
                    .toList();
        }
    }

    /**
     * An item an offer names by itself whose stock, across every warehouse, is gone or down to its minimum:
     * a promotion that sells out leaves the shelf promising what the till cannot hand over. One key per item.
     */
    public static final class ShortOfStock implements NotificationSource {

        public static final String ID = "offers.short-stock";
        private final NotificationCommand openOffers;

        public ShortOfStock(NotificationCommand openOffers) {
            this.openOffers = openOffers;
        }

        @NotNull
        @Override
        public String id() {
            return ID;
        }

        @NotNull
        @Override
        public String category() {
            return NotificationCategories.ITEMS;
        }

        @NotNull
        @Override
        public String displayName() {
            return text("offer.notification.stock.name");
        }

        @NotNull
        @Override
        public Duration interval() {
            return Duration.ofHours(1);
        }

        @Override
        public boolean enabled() {
            return readable();
        }

        @NotNull
        @Override
        public List<AppNotification> poll() throws Exception {
            OfferService service = ServiceRegistry.get(OfferService.class);
            if (service == null || !service.enabled()) {
                return List.of();
            }
            return service.shortOfStock(LocalDate.now()).stream()
                    .map(item -> AppNotification.builder(ID + "." + item.item().itemId())
                            .category(category())
                            .severity(NotificationSeverity.WARNING)
                            .title(text("offer.notification.stock.title"))
                            .message(LanguageManager.getInstance().getString("offer.notification.stock.message",
                                    item.item().name(),
                                    item.offers().stream().map(Offer::name).collect(Collectors.joining(
                                            text("offer.notification.stock.separator"))),
                                    item.item().balance().stripTrailingZeros().toPlainString()))
                            .payload(item)
                            .onOpen(text("offers.title"), openOffers)
                            .build())
                    .toList();
        }
    }

    private static boolean readable() {
        OfferService service = ServiceRegistry.get(OfferService.class);
        return service != null && service.enabled() && AuthorizationGuard.isGranted(AppPermissions.OFFER_SHOW);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
