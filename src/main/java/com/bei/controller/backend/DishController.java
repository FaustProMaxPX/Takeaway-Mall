package com.bei.controller.backend;

import com.bei.annotation.Cache;
import com.bei.annotation.CleanCache;
import com.bei.common.BusinessException;
import com.bei.common.CommonResult;
import com.bei.dto.DishDto;
import com.bei.dto.param.PageParam;
import com.bei.model.Category;
import com.bei.model.Dish;
import com.bei.model.DishFlavor;
import com.bei.model.SetmealDish;
import com.bei.service.CategoryService;
import com.bei.service.DishFlavorService;
import com.bei.service.DishService;
import com.bei.service.SetmealDishService;
import com.bei.vo.DishVo;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dish")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private DishFlavorService dishFlavorService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SetmealDishService setmealDishService;

    @PostMapping("")
    @Transactional
    @CleanCache(name = "dish")
    public CommonResult addDish(@RequestBody DishDto dishParam) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishParam, dish);
        Long dishId = dishService.addDish(dish);
        dishFlavorService.addDishFlavorBatch(dishParam.getFlavors(), dishId);
        return CommonResult.success(dishId);
    }

    @GetMapping("/page")
    @Cache(name = "dishPage")
    public CommonResult getDishPage(PageParam pageParam) {
        List<Dish> dishList = dishService.getDishPage(pageParam.getPage(), pageParam.getPageSize(), pageParam.getName());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd");
        PageInfo pageInfo = new PageInfo(dishList);
        List list = (List) pageInfo.getList().stream()
                .map(o -> {
                    Dish dish = (Dish) o;
                    DishVo dishVo = new DishVo();
                    BeanUtils.copyProperties(dish, dishVo);
                    Category category = categoryService.getCategoryById(dish.getCategoryId());
                    dishVo.setUpdateTime(dateFormat.format(dish.getUpdateTime()));
                    dishVo.setCategoryName(category.getName());
                    return dishVo;
                })
                .collect(Collectors.toList());
        pageInfo.setList(list);
        return CommonResult.success(pageInfo);
    }

    @GetMapping("/{id}")
    public CommonResult getDishDetail(@PathVariable Long id) {
        Dish dish = dishService.getDishById(id);
        if (dish == null) {
            return CommonResult.error("????????????????????????");
        }
        List<DishFlavor> flavors = dishFlavorService.getFlavorByDish(id);
        DishDto dishDto = new DishDto();
        BeanUtils.copyProperties(dish, dishDto);
        dishDto.setFlavors(flavors);
        return CommonResult.success(dishDto);
    }

    @PutMapping
    @Transactional
    @CleanCache(name = "dish")
    public CommonResult updateDish(@RequestBody DishDto dishDto) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDto, dish);
        int count = dishService.updateDish(dish);
        if (count != 1) {
            log.debug("???????????? [" + dishDto + "] ??????");
            return CommonResult.error( "??????????????????");
        }
        dishFlavorService.removeByDish(dish.getId());
        dishFlavorService.addDishFlavorBatch(dishDto.getFlavors(), dish.getId());
        return CommonResult.success(dish.getId());
    }

    @GetMapping("/list")
    @Cache(name = "dishList")
    public CommonResult getDishList(Dish dish) {
        if (dish.getCategoryId() == null) {
            return CommonResult.error("??????????????????");
        }
        List<Dish> dishList = dishService.getDishByCategory(dish.getCategoryId());
        List<DishDto> dishDtos = dishList.stream()
                .filter(dish1 -> {
                    return Objects.equals(dish1.getStatus(), dish.getStatus());
                })
                .map(dish1 -> {
                    DishDto dishDto = new DishDto();
                    BeanUtils.copyProperties(dish1, dishDto);
                    Category category = categoryService.getCategoryById(dish1.getCategoryId());
                    dishDto.setCategoryName(category.getName());
                    List<DishFlavor> flavors = dishFlavorService.getFlavorByDish(dish1.getId());
                    dishDto.setFlavors(flavors);
                    return dishDto;
                })
                .collect(Collectors.toList());
        return CommonResult.success(dishDtos);
    }

    @DeleteMapping
    @Transactional
    @CleanCache(name = "dish")
    public CommonResult deleteDish(String ids) {
        List<Long> list = convertIdsToList(ids);
        for (Long id : list) {
            Dish dish = dishService.getDishById(id);
            if (dish.getStatus() == 1) {
                throw new BusinessException("????????????????????????????????????");
            }
        }
        int count = dishService.deleteDishBatches(list);
        if (count == 0) {
            log.debug("?????? " + ids + " ????????????????????????????????????");
            throw new BusinessException("????????????,?????????????????????");
        }
        count = dishFlavorService.deleteDishBatches(list);
        if (count == 0) {
            log.debug("?????? " + ids + " ??????????????????????????????????????????????????????");
            throw new BusinessException("??????????????????");
        }
        for (Long id : list) {
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(1);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("??????????????????");
    }

    @PostMapping("/status/0")
    @CleanCache(name = "dish")
    public CommonResult disableDish(String ids) {
        List<Long> idList = convertIdsToList(ids);
        for (Long id : idList) {
            Dish dish = new Dish();
            dish.setId(id);
            dish.setStatus(0);
            int count = dishService.updateDish(dish);
            if (count != 1) {
                log.debug("?????? " + id + " ??????????????????????????????????????????");
                throw new BusinessException("??????????????????????????????????????????");
            }
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(1);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("????????????");
    }

    @PostMapping("/status/1")
    @CleanCache(name = "dish")
    public CommonResult enableDish(String ids) {
        List<Long> idList = convertIdsToList(ids);
        for (Long id : idList) {
            Dish dish = new Dish();
            dish.setId(id);
            dish.setStatus(1);
            int count = dishService.updateDish(dish);
            if (count != 1) {
                log.debug("?????? " + ids + " ??????????????????????????????????????????");
                throw new BusinessException("??????????????????????????????????????????");
            }
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(0);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("????????????");
    }

    private List<Long> convertIdsToList(String ids) {
        String[] idList = ids.split(",");
        List<Long> list = Arrays.stream(idList)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return list;
    }
}
