package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.PetProfileAddRequest;
import com.pethome.dto.PetProfileUpdateRequest;
import com.pethome.service.PetProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 宠物档案管理
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验），
 * 归属用户由拦截器写入 ThreadLocal 后由 Service 层自动绑定。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/pet")
@RequiredArgsConstructor
public class UserPetController {

    private final PetProfileService petProfileService;

    /**
     * 查询当前用户所有宠物
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(petProfileService.listForCurrentUser());
    }

    /**
     * 查询当前用户的单只宠物详情
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Min(value = 1, message = "宠物id不合法") Long id) {
        return Result.success(petProfileService.detailForCurrentUser(id));
    }

    /**
     * 新增宠物
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid PetProfileAddRequest request) {
        return Result.success(petProfileService.add(request));
    }

    /**
     * 修改宠物信息
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid PetProfileUpdateRequest request) {
        petProfileService.update(request);
        return Result.success();
    }

    /**
     * 删除宠物
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "宠物id不合法") Long id) {
        petProfileService.delete(id);
        return Result.success();
    }

}
