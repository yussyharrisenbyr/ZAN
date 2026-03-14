package com.example.dianzan.util;

import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BlogImageUtils {

    private BlogImageUtils() {
    }

    public static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static List<String> normalizeImageList(Collection<String> imageList) {
        if (imageList == null || imageList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> orderedUnique = new LinkedHashSet<>();
        for (String image : imageList) {
            String normalized = normalizeUrl(image);
            if (normalized != null) {
                orderedUnique.add(normalized);
            }
        }
        return orderedUnique.isEmpty() ? Collections.emptyList() : new ArrayList<>(orderedUnique);
    }

    public static List<String> parseImageUrls(String imageUrlsJson) {
        String normalizedJson = normalizeUrl(imageUrlsJson);
        if (normalizedJson == null) {
            return Collections.emptyList();
        }
        try {
            return normalizeImageList(JSONUtil.parseArray(normalizedJson).toList(String.class));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static List<String> resolveImageList(Collection<String> imageList, String imageUrlsJson) {
        if (imageList != null) {
            return normalizeImageList(imageList);
        }
        return parseImageUrls(imageUrlsJson);
    }

    public static String toImageUrlsJson(Collection<String> imageList) {
        List<String> normalized = normalizeImageList(imageList);
        return normalized.isEmpty() ? null : JSONUtil.toJsonStr(normalized);
    }

    public static String resolveCoverImg(String coverImg, Collection<String> imageList) {
        String normalizedCover = normalizeUrl(coverImg);
        if (normalizedCover != null) {
            return normalizedCover;
        }
        List<String> normalizedImages = normalizeImageList(imageList);
        return normalizedImages.isEmpty() ? null : normalizedImages.getFirst();
    }
}
