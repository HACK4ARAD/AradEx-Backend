package aradexperience.repository.admin;

import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** T14: lista de utilizatori din `/admin/users` (rol + căutare după nume, email sau telefon). `q` vine ca `%text%`. */
public interface AdminUserRepository extends Repository<User, Long> {

    @Query("""
            select u from User u where u.role in :roles
              and (lower(u.displayName) like :q or lower(coalesce(u.email, '')) like :q or coalesce(u.phone, '') like :q)""")
    Page<User> search(@Param("roles") Collection<Role> roles, @Param("q") String q, Pageable page);

    @Query("select b from Business b where b.ownerUserId in :ownerIds")
    List<Business> businessesOf(@Param("ownerIds") Collection<Long> ownerIds);

    @Query("select t.userId as userId, count(t) as total from Ticket t where t.userId in :ids and t.status <> :cancelled group by t.userId")
    List<TicketCount> ticketCounts(@Param("ids") Collection<Long> ids, @Param("cancelled") TicketStatus cancelled);

    interface TicketCount {
        Long getUserId();
        Long getTotal();
    }
}
