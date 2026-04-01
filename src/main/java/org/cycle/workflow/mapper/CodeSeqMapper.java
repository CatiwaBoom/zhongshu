package org.cycle.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.cycle.workflow.entity.CodeSeqEntity;

@Mapper
public interface CodeSeqMapper {

    @Select("SELECT SEQ_DATE AS seqDate, LAST_SEQ AS lastSeq FROM WF_CODE_SEQ WHERE SEQ_DATE = #{date}")
    CodeSeqEntity selectByDate(@Param("date") String date);

    @Update("UPDATE WF_CODE_SEQ SET LAST_SEQ = LAST_SEQ + 1 WHERE SEQ_DATE = #{date}")
    int increment(@Param("date") String date);

    @Insert("INSERT INTO WF_CODE_SEQ(SEQ_DATE, LAST_SEQ) VALUES(#{date}, 1)")
    int insertNew(@Param("date") String date);
}

