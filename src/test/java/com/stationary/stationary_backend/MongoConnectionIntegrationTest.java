package com.stationary.stationary_backend;

import com.stationary.stationary_backend.model.Category;
import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.stationary.stationary_backend.model.embedded.ImageData;
import com.stationary.stationary_backend.repository.CategoryRepository;
import com.stationary.stationary_backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class MongoConnectionIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testMongoDbConnectionAndInsert() {
        System.out.println(">>> Starting live MongoDB Atlas connection test...");

        // Create a test category
        Category testCategory = Category.builder()
                .name("Atlas Test Pens")
                .slug("atlas-test-pens")
                .imageUrl("https://example.com/test-image.jpg")
                .isActive(true)
                .build();

        Category savedCategory = categoryRepository.save(testCategory);
        assertNotNull(savedCategory.getId(), "Category should be saved and assigned an ID");
        System.out.println(">>> Category saved successfully with ID: " + savedCategory.getId());

        // Create a test product linked to this category
        Product testProduct = Product.builder()
                .name("Atlas Gel Pen")
                .slug("atlas-gel-pen")
                .description("Test pen connecting to MongoDB Atlas directly")
                .price(20.00)
                .stock(100)
                .categoryId(savedCategory.getId())
                .image(ImageData.builder()
                        .publicId("test/atlas")
                        .url("https://example.com/test.jpg")
                        .build())
                .status(ProductStatus.ACTIVE)
                .tags(List.of("test", "atlas"))
                .build();

        Product savedProduct = productRepository.save(testProduct);
        assertNotNull(savedProduct.getId(), "Product should be saved and assigned an ID");
        System.out.println(">>> Product saved successfully with ID: " + savedProduct.getId());

        // Clean up test data so we don't pollute the user's database
        productRepository.delete(savedProduct);
        categoryRepository.delete(savedCategory);
        System.out.println(">>> Test data cleaned up successfully.");
        System.out.println(">>> SUCCESS: Connected to MongoDB Atlas and verified operations!");
    }
}
