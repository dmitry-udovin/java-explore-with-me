package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.category.CategoryDto;
import ru.practicum.ewm.dto.category.NewCategoryDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CategoryMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.util.PaginationUtil;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryDto create(NewCategoryDto dto) {
        Category category = Category.builder().name(dto.getName()).build();
        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long catId, CategoryDto dto) {
        Category category = getCategoryOrThrow(catId);
        category.setName(dto.getName());
        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long catId) {
        getCategoryOrThrow(catId);
        if (eventRepository.countByCategoryId(catId) > 0) {
            throw new ConflictException("The category is not empty");
        }
        categoryRepository.deleteById(catId);
    }

    @Transactional(readOnly = true)
    public CategoryDto getById(Long catId) {
        return categoryMapper.toDto(getCategoryOrThrow(catId));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAll(int from, int size) {
        List<Category> categories = categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getId))
                .toList();
        return PaginationUtil.paginate(categories, from, size).stream()
                .map(categoryMapper::toDto)
                .toList();
    }

    private Category getCategoryOrThrow(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}
