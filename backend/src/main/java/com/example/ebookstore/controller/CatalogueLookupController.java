package com.example.ebookstore.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.CategoryResponse;
import com.example.ebookstore.dto.PublisherResponse;
import com.example.ebookstore.service.CatalogueService;

/**
 * Public lists used to fill the "category" and "publisher" filters
 * (operations listCategories and listPublishers in openapi.yaml). Read-only.
 */
@RestController
public class CatalogueLookupController {

    private final CatalogueService catalogueService;

    public CatalogueLookupController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> listCategories() {
        return catalogueService.listCategories();
    }

    @GetMapping("/api/publishers")
    public List<PublisherResponse> listPublishers() {
        return catalogueService.listPublishers();
    }
}
