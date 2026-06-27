package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.model.Category;
import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.stationary.stationary_backend.model.embedded.ImageData;
import com.stationary.stationary_backend.repository.CategoryRepository;
import com.stationary.stationary_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DemoDataSeedRunner — Seeds initial categories and products if the database is empty.
 *
 * Runs after AdminSeedRunner (Order 2) so admin is seeded first.
 * Idempotent: Only seeds if collection counts are 0.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Runs after AdminSeedRunner (Order 1 by default, or just runs sequentially)
public class DemoDataSeedRunner implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            log.info("Database already contains categories. Skipping demo data seeding.");
            return;
        }

        log.info("Seeding realistic demo stationery categories and products...");

        // ── Seed Categories ──────────────────────────────────────────────────
        Category writing = Category.builder()
                .name("Pens & Writing")
                .slug("pens-writing")
                .imageUrl("https://images.unsplash.com/photo-1513542789411-b6a5d4f31634?auto=format&fit=crop&w=400&q=80")
                .isActive(true)
                .build();

        Category notebooks = Category.builder()
                .name("Notebooks & Journals")
                .slug("notebooks-journals")
                .imageUrl("https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&w=400&q=80")
                .isActive(true)
                .build();

        Category desk = Category.builder()
                .name("Desk Organizers")
                .slug("desk-organizers")
                .imageUrl("https://images.unsplash.com/photo-1585776245991-cf89dd7fc73a?auto=format&fit=crop&w=400&q=80")
                .isActive(true)
                .build();

        categoryRepository.saveAll(List.of(writing, notebooks, desk));
        log.info("Seeded 3 product categories.");

        // ── Seed Products ───────────────────────────────────────────────────
        Product p1 = Product.builder()
                .name("Premium Gel Pen Set (Black)")
                .slug("premium-gel-pen-set-black")
                .description("Box of 12 ultra-smooth black gel pens with comfortable rubber grip. 0.5mm fine tip, smudge-proof quick-drying ink.")
                .price(150.00)
                .stock(50)
                .categoryId(writing.getId())
                .image(ImageData.builder()
                        .publicId("demo/pens")
                        .url("https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=600&q=80")
                        .build())
                .status(ProductStatus.ACTIVE)
                .tags(List.of("pens", "gel-pen", "writing", "premium"))
                .build();

        Product p2 = Product.builder()
                .name("Leatherette Dotted Journal")
                .slug("leatherette-dotted-journal")
                .description("A5 Size faux leather bullet journal, 160 pages of 120GSM bleed-resistant cream paper. Dotted grid layout with expandable back pocket.")
                .price(350.00)
                .stock(35)
                .categoryId(notebooks.getId())
                .image(ImageData.builder()
                        .publicId("demo/journal")
                        .url("https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=600&q=80")
                        .build())
                .status(ProductStatus.ACTIVE)
                .tags(List.of("notebooks", "journal", "bullet-journal", "dotted"))
                .build();

        Product p3 = Product.builder()
                .name("Metal Mesh Desk Organizer")
                .slug("metal-mesh-desk-organizer")
                .description("Multi-compartment space-saving desk caddy. Features 6 compartments plus 1 sliding drawer for sticky notes, pens, and paperclips.")
                .price(499.00)
                .stock(12)
                .categoryId(desk.getId())
                .image(ImageData.builder()
                        .publicId("demo/organizer")
                        .url("https://images.unsplash.com/photo-1596717524941-8636e2f42a03?auto=format&fit=crop&w=600&q=80")
                        .build())
                .status(ProductStatus.ACTIVE)
                .tags(List.of("organizers", "desk", "office", "metal"))
                .build();

        Product p4 = Product.builder()
                .name("Pastel Sticky Notes (400 Sheets)")
                .slug("pastel-sticky-notes-400-sheets")
                .description("Pack of 4 pastel shade sticky pads (Pink, Blue, Yellow, Green), 3x3 inches size. Super sticky adhesive backing, peels cleanly off pages.")
                .price(99.00)
                .stock(100)
                .categoryId(writing.getId())
                .image(ImageData.builder()
                        .publicId("demo/sticky")
                        .url("https://images.unsplash.com/photo-1603517865664-df0a25695029?auto=format&fit=crop&w=600&q=80")
                        .build())
                .status(ProductStatus.ACTIVE)
                .tags(List.of("sticky-notes", "pastel", "stationery"))
                .build();

        productRepository.saveAll(List.of(p1, p2, p3, p4));
        log.info("Seeded 4 demo products.");
    }
}
