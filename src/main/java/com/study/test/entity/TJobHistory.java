package com.study.test.entity;

public class TJobHistory extends BaseEntity {
    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column t_job_history.id
     *
     * @mbggenerated
     */
    private Integer id;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column t_job_history.user_id
     *
     * @mbggenerated
     */
    private Integer userId;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column t_job_history.comp_name
     *
     * @mbggenerated
     */
    private String compName;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column t_job_history.years
     *
     * @mbggenerated
     */
    private Integer years;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column t_job_history.title
     *
     * @mbggenerated
     */
    private String title;

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column t_job_history.id
     *
     * @return the value of t_job_history.id
     *
     * @mbggenerated
     */
    public Integer getId() {
        return id;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column t_job_history.id
     *
     * @param id the value for t_job_history.id
     *
     * @mbggenerated
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column t_job_history.user_id
     *
     * @return the value of t_job_history.user_id
     *
     * @mbggenerated
     */
    public Integer getUserId() {
        return userId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column t_job_history.user_id
     *
     * @param userId the value for t_job_history.user_id
     *
     * @mbggenerated
     */
    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column t_job_history.comp_name
     *
     * @return the value of t_job_history.comp_name
     *
     * @mbggenerated
     */
    public String getCompName() {
        return compName;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column t_job_history.comp_name
     *
     * @param compName the value for t_job_history.comp_name
     *
     * @mbggenerated
     */
    public void setCompName(String compName) {
        this.compName = compName;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column t_job_history.years
     *
     * @return the value of t_job_history.years
     *
     * @mbggenerated
     */
    public Integer getYears() {
        return years;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column t_job_history.years
     *
     * @param years the value for t_job_history.years
     *
     * @mbggenerated
     */
    public void setYears(Integer years) {
        this.years = years;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column t_job_history.title
     *
     * @return the value of t_job_history.title
     *
     * @mbggenerated
     */
    public String getTitle() {
        return title;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column t_job_history.title
     *
     * @param title the value for t_job_history.title
     *
     * @mbggenerated
     */
    public void setTitle(String title) {
        this.title = title;
    }
}
