package com.gijela.morpheus.chatflow.vo;

import java.util.List;

public class ChatflowSessionPageVO {

    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private List<ChatflowSessionVO> list;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<ChatflowSessionVO> getList() {
        return list;
    }

    public void setList(List<ChatflowSessionVO> list) {
        this.list = list;
    }
}
