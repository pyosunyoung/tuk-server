package com.tuk.place.controller;

import com.tuk.place.dto.PlaceSearchResult;
import com.tuk.place.service.PlaceSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 장소 검색 — 로그인한 사용자만(SecurityConfig 의 anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    /** 키워드 장소 검색. 예: /api/places/search?query=강남역 스타벅스 */
    @GetMapping("/search")
    public List<PlaceSearchResult> search(@RequestParam(defaultValue = "") String query) {
        return placeSearchService.search(query);
    }
}
