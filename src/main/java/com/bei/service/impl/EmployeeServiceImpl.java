package com.bei.service.impl;

import com.bei.mapper.EmployeeMapper;
import com.bei.model.Employee;
import com.bei.model.EmployeeExample;
import com.bei.service.EmployeeService;
import com.bei.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Import({BCryptPasswordEncoder.class})
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /**
     * 根据用户名获取员工
     * @param username 用户名
     * */
    @Override
    public Employee getEmployeeByUsername(String username) {
        EmployeeExample example = new EmployeeExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<Employee> employees = employeeMapper.selectByExample(example);
        if (employees != null && employees.size() > 0) {
            return employees.get(0);
        }
        return null;
    }

    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return token
     * */
    public String login(String username, String password) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        // 校验密码
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new BadCredentialsException("密码错误");
        }
        if (!userDetails.isAccountNonLocked()) {
//            throw new
        }
        // 将登录成功的用户交给spring security
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        // 返回生成的token
        return jwtTokenUtil.generateToken(userDetails);
    }
}
