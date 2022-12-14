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
            return CommonResult.error("没有该菜品的信息");
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
            log.debug("更新菜品 [" + dishDto + "] 失败");
            return CommonResult.error( "更新菜品失败");
        }
        dishFlavorService.removeByDish(dish.getId());
        dishFlavorService.addDishFlavorBatch(dishDto.getFlavors(), dish.getId());
        return CommonResult.success(dish.getId());
    }

    @GetMapping("/list")
    @Cache(name = "dishList")
    public CommonResult getDishList(Dish dish) {
        if (dish.getCategoryId() == null) {
            return CommonResult.error("分类参数为空");
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
                throw new BusinessException("选中的部分菜品正在售卖中");
            }
        }
        int count = dishService.deleteDishBatches(list);
        if (count == 0) {
            log.debug("删除 " + ids + " 失败，数据库中缺少该记录");
            throw new BusinessException("删除失败,该菜品未被记录");
        }
        count = dishFlavorService.deleteDishBatches(list);
        if (count == 0) {
            log.debug("删除 " + ids + " 失败，没有删除菜品口味关系表中的记录");
            throw new BusinessException("删除菜品失败");
        }
        for (Long id : list) {
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(1);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("删除菜品成功");
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
                log.debug("停售 " + id + " 失败，数据库没有找到操作对象");
                throw new BusinessException("停售失败，请检查参数是否正确");
            }
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(1);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("停售成功");
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
                log.debug("启售 " + ids + " 失败，数据库没有找到操作对象");
                throw new BusinessException("启售失败，请检查参数是否正确");
            }
            SetmealDish setmealDish = new SetmealDish();
            setmealDish.setDishId(String.valueOf(id));
            setmealDish.setIsDeleted(0);
            setmealDishService.updateSetmeal(setmealDish);
        }
        return CommonResult.success("启售成功");
    }

    private List<Long> convertIdsToList(String ids) {
        String[] idList = ids.split(",");
        List<Long> list = Arrays.stream(idList)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return list;
    }
}
