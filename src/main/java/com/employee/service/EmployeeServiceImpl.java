package com.employee.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.employee.entity.Employee;
import com.employee.repository.EmployeeRepository;

@Service
public class EmployeeServiceImpl implements EmployeeService{
	
	@Autowired
    private EmployeeRepository employeeRepository;

    @Override
    public List<Employee> getAllEmployees() {
        // Все пользователи видят ВСЕ строки — фильтрации нет
        return (List<Employee>) employeeRepository.findAll();
    }

    @Override
    public Employee getEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found: " + employeeId));
    }

    @Override
    public Employee addEmployee(Employee employee) {
        // Принудительно устанавливаем владельца = имя текущего пользователя
        employee.setOwner(getCurrentUsername());
        return employeeRepository.save(employee);
    }
    
    @Override
    public Employee updateEmployee(Employee employee) {
        // 1. Загружаем существующую запись из БД
        Employee existing = employeeRepository.findById(employee.getEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found: " + employee.getEmployeeId()));

        // 2. Проверяем права: admin может всё, остальные — только свои записи
        if (!canModify(existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only edit your own records");
        }

        // 3. Поле owner нельзя изменить через API — всегда оставляем исходное
        employee.setOwner(existing.getOwner());

        return employeeRepository.save(employee);
    }
    
    @Override
    public String deleteEmployee(Employee employee) {
        // 1. Загружаем существующую запись
        Employee existing = employeeRepository.findById(employee.getEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found: " + employee.getEmployeeId()));

        // 2. Проверяем права
        if (!canModify(existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only delete your own records");
        }

        employeeRepository.delete(existing);
        return "Employee is Deleted Successfully for employeeId: " + employee.getEmployeeId();
    }
    
    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============

    /**
     * Имя текущего залогиненного пользователя ("Admin", "Fil" или "Bob").
     * Возвращает null, если никто не залогинен (не должно случаться при защищённых эндпоинтах).
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
    }

    /**
     * true, если текущий пользователь — admin ИЛИ владелец записи.
     */
    private boolean canModify(Employee employee) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        // Admin может всё
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_admin"));
        if (isAdmin) return true;

        // Остальные — только свои записи
        return auth.getName().equals(employee.getOwner());
    }
}
