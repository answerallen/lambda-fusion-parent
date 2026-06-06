package com.lambda.fusion.authority.user.service.impl;

import com.lambda.fusion.authority.AuthorityConstants.ThirdType;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.user.mapper.UserThirdPartMapper;
import com.lambda.fusion.authority.user.model.ThirdPartBinding;
import com.lambda.fusion.authority.user.model.entity.UserThirdPartEntity;
import com.lambda.fusion.authority.user.service.UserThirdPartService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserThirdPartServiceImpl implements UserThirdPartService {

    private final UserThirdPartMapper userThirdpartMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(String username, String loginType, String openId) {
        String existing = userThirdpartMapper.findUsernameByThirdTypeAndOpenId(loginType, openId);
        if (existing != null) {
            if (existing.equals(username)) {
                throw AuthorityBusinessException.invalidParameter("该第三方账号已绑定当前用户");
            }
            throw AuthorityBusinessException.invalidParameter("该第三方账号已被其他用户绑定");
        }

        UserThirdPartEntity entity = new UserThirdPartEntity();
        entity.setUsername(username);
        entity.setThirdType(loginType);
        entity.setOpenId(openId);
        userThirdpartMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String loginType, String username) {
        int deleted = userThirdpartMapper.deleteByUsernameAndThirdType(username, loginType);
        if (deleted == 0) {
            throw AuthorityBusinessException.invalidParameter("未找到该第三方绑定信息");
        }
    }

    @Override
    public List<ThirdPartBinding> listByUsername(String username) {
        List<UserThirdPartEntity> entities = userThirdpartMapper.findByUsername(username);
        return entities.stream()
                .map(entity -> {
                    String label = resolveLabel(entity.getThirdType());
                    return new ThirdPartBinding(entity.getThirdType(), label, entity.getOpenId(), entity.getUsername());
                })
                .toList();
    }

    @Override
    public String findUsername(String loginType, String openId) {
        return userThirdpartMapper.findUsernameByThirdTypeAndOpenId(loginType, openId);
    }

    private String resolveLabel(String loginType) {
        try {
            return ThirdType.of(loginType).getLabel();
        } catch (IllegalArgumentException e) {
            return loginType;
        }
    }
}
