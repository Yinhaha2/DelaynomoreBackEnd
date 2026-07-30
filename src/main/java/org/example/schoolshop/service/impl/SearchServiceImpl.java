package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.SearchHot;
import org.example.schoolshop.mapper.SearchHotMapper;
import org.example.schoolshop.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchHotMapper searchHotMapper;

    @Override
    public Map<String, Object> hot() {
        List<SearchHot> words = searchHotMapper.selectList(new LambdaQueryWrapper<SearchHot>()
                .orderByDesc(SearchHot::getHeat).last("LIMIT 10"));
        if (words.isEmpty()) {
            SearchHot h1 = new SearchHot();
            h1.setWord("代取快递");
            h1.setHeat(980);
            SearchHot h2 = new SearchHot();
            h2.setWord("期末笔记");
            h2.setHeat(860);
            words = List.of(h1, h2);
        }
        List<Map<String, Object>> list = words.stream().map(w -> {
            Map<String, Object> m = new HashMap<>();
            m.put("word", w.getWord());
            m.put("heat", w.getHeat());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return data;
    }
}
