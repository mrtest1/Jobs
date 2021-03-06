/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.zford.jobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import me.zford.jobs.config.ConfigManager;
import me.zford.jobs.container.Job;
import me.zford.jobs.container.JobProgression;
import me.zford.jobs.container.JobsPlayer;
import me.zford.jobs.container.Title;
import me.zford.jobs.dao.JobsDAO;
import me.zford.jobs.i18n.Language;
import me.zford.jobs.util.ChatColor;

public class PlayerManager {
    private Map<String, JobsPlayer> players = Collections.synchronizedMap(new HashMap<String, JobsPlayer>());
    
    /**
     * Handles join of new player
     * @param playername
     */
    public void playerJoin(String playername) {
        synchronized (players) {
            JobsPlayer jPlayer = players.get(playername);
            if (jPlayer == null) {
                jPlayer = new JobsPlayer(playername);
                jPlayer.loadDAOData(Jobs.getJobsDAO().getAllJobs(jPlayer));
                players.put(playername, jPlayer);
            }
            jPlayer.onConnect();
            jPlayer.reloadHonorific();
            Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
        }
    }
    
    /**
     * Handles player quit
     * @param playername
     */
    public void playerQuit(String playername) {
        synchronized (players) {
            if (ConfigManager.getJobsConfiguration().saveOnDisconnect()) {
                JobsPlayer jPlayer = players.remove(playername);
                if (jPlayer != null) {
                    jPlayer.save(Jobs.getJobsDAO());
                    jPlayer.onDisconnect();
                }
            } else {
                JobsPlayer jPlayer = players.get(playername);
                if (jPlayer != null) {
                    jPlayer.onDisconnect();
                }
            }
        }
    }
    
    /**
     * Save all the information of all of the players in the game
     */
    public void saveAll() {
        JobsDAO dao = Jobs.getJobsDAO();
        
        /*
         * Saving is a three step process to minimize synchronization locks when called asynchronously.
         * 
         * 1) Safely copy list for saving.
         * 2) Perform save on all players on copied list.
         * 3) Garbage collect the real list to remove any offline players with saved data
         */
        ArrayList<JobsPlayer> list = null;
        synchronized (players) {
            list = new ArrayList<JobsPlayer>(players.values());
        }
        
        for (JobsPlayer jPlayer : list) {
            jPlayer.save(dao);
        }
        
        synchronized (players) {
            Iterator<JobsPlayer> iter = players.values().iterator();
            while (iter.hasNext()) {
                JobsPlayer jPlayer = iter.next();
                synchronized (jPlayer.saveLock) {
                    if (!jPlayer.isOnline() && jPlayer.isSaved()) {
                        iter.remove();
                    }
                }
            }
        }
    }
    
    /**
     * Get the player job info for specific player
     * @param player - the player who's job you're getting
     * @return the player job info of the player
     */
    public JobsPlayer getJobsPlayer(String playername) {
        JobsPlayer jPlayer = players.get(playername);
        if (jPlayer == null) {
            jPlayer = new JobsPlayer(playername);
            jPlayer.loadDAOData(Jobs.getJobsDAO().getAllJobs(jPlayer));
        }
        return jPlayer;
    }
    
    /**
     * Causes player to join their job
     * @param jPlayer
     * @param job
     */
    public void joinJob(JobsPlayer jPlayer, Job job) {
        synchronized (jPlayer.saveLock) {
            if (jPlayer.isInJob(job))
                return;
            // let the user join the job
            if (!jPlayer.joinJob(job))
                return;
            
            Jobs.getJobsDAO().joinJob(jPlayer, job);
            Jobs.takeSlot(job);
        }
    }
    
    /**
     * Causes player to leave their job
     * @param jPlayer
     * @param job
     */
    public void leaveJob(JobsPlayer jPlayer, Job job) {
        synchronized (jPlayer.saveLock) {
            if (!jPlayer.isInJob(job))
                return;
            // let the user leave the job
            if (!jPlayer.leaveJob(job))
                return;
            
            Jobs.getJobsDAO().quitJob(jPlayer, job);
            Jobs.leaveSlot(job);
        }
    }
    
    /**
     * Causes player to leave all their jobs
     * @param jPlayer
     */
    public void leaveAllJobs(JobsPlayer jPlayer) {
        synchronized (jPlayer.saveLock) {
            for (JobProgression job : jPlayer.getJobProgression()) {
                Jobs.getJobsDAO().quitJob(jPlayer, job.getJob());
                Jobs.leaveSlot(job.getJob());
            }
            
            jPlayer.leaveAllJobs();
        }
    }
    
    /**
     * Transfers player job
     * @param jPlayer
     * @param oldjob - the old job
     * @param newjob - the new job
     */
    public void transferJob(JobsPlayer jPlayer, Job oldjob, Job newjob) {
        synchronized (jPlayer.saveLock) {
            if (!jPlayer.transferJob(oldjob,  newjob))
                return;
            
            JobsDAO dao = Jobs.getJobsDAO();
            dao.quitJob(jPlayer, oldjob);
            dao.joinJob(jPlayer, newjob);
            jPlayer.save(dao);
        }
    }
    
    /**
     * Promotes player in their job
     * @param jPlayer
     * @param job - the job
     * @param levels - number of levels to promote
     */
    public void promoteJob(JobsPlayer jPlayer, Job job, int levels) {
        synchronized (jPlayer.saveLock) {
            jPlayer.promoteJob(job, levels);
            jPlayer.save(Jobs.getJobsDAO());
        }
    }
    
    /**
     * Demote player in their job
     * @param jPlayer
     * @param job - the job
     * @param levels - number of levels to demote
     */
    public void demoteJob(JobsPlayer jPlayer, Job job, int levels) {
        synchronized (jPlayer.saveLock) {
            jPlayer.demoteJob(job, levels);
            jPlayer.save(Jobs.getJobsDAO());
        }
    }
    
    /**
     * Adds experience to the player
     * @param jPlayer
     * @param job - the job
     * @param experience - experience gained
     */
    public void addExperience(JobsPlayer jPlayer, Job job, double experience) {
        synchronized (jPlayer.saveLock) {
            JobProgression prog = jPlayer.getJobProgression(job);
            if (prog == null)
                return;
            if (prog.addExperience(experience))
                performLevelUp(jPlayer, job);
    
            jPlayer.save(Jobs.getJobsDAO());
        }
    }
    
    /**
     * Removes experience to the player
     * @param jPlayer
     * @param job - the job
     * @param experience - experience gained
     */
    public void removeExperience(JobsPlayer jPlayer, Job job, double experience) {
        synchronized (jPlayer.saveLock) {
            JobProgression prog = jPlayer.getJobProgression(job);
            if (prog == null)
                return;
            prog.addExperience(-experience);
            
            jPlayer.save(Jobs.getJobsDAO());
        }
    }
    
    
    /**
     * Broadcasts level up about a player
     * @param jPlayer
     * @param job
     */
    public void performLevelUp(JobsPlayer jPlayer, Job job) {
        Player player = Jobs.getServer().getPlayer(jPlayer.getName());
        JobProgression prog = jPlayer.getJobProgression(job);
        if (prog == null)
            return;
        
        String message;
        if (ConfigManager.getJobsConfiguration().isBroadcastingLevelups()) {
            message = Language.getMessage("message.levelup.broadcast");
        } else {
            message = Language.getMessage("message.levelup.nobroadcast");
        }
        message = message.replace("%jobname%", job.getChatColor() + job.getName() + ChatColor.WHITE);
        if (prog.getTitle() != null) {
            message = message.replace("%titlename%", prog.getTitle().getChatColor() + prog.getTitle().getName() + ChatColor.WHITE);
        }
        if (player != null) {
            message = message.replace("%playername%", player.getDisplayName());
        } else {
            message = message.replace("%playername%", jPlayer.getName());
        }
        message = message.replace("%joblevel%", ""+prog.getLevel());
        for (String line: message.split("\n")) {
            if (ConfigManager.getJobsConfiguration().isBroadcastingLevelups()) {
                Jobs.getServer().broadcastMessage(line);
            } else if (player != null) {
                player.sendMessage(line);
            }
        }
        
        Title levelTitle = ConfigManager.getJobsConfiguration().getTitleForLevel(prog.getLevel());
        if (levelTitle != null && !levelTitle.equals(prog.getTitle())) {        
            // user would skill up
            if (ConfigManager.getJobsConfiguration().isBroadcastingSkillups()) {
                message = Language.getMessage("message.skillup.broadcast");
            } else {
                message = Language.getMessage("message.skillup.nobroadcast");
            }
            if (player != null) {
                message = message.replace("%playername%", player.getDisplayName());
            } else {
                message = message.replace("%playername%", jPlayer.getName());
            }
            message = message.replace("%titlename%", levelTitle.getChatColor() + levelTitle.getName() + ChatColor.WHITE);
            message = message.replace("%jobname%", job.getChatColor() + job.getName() + ChatColor.WHITE);
            for (String line: message.split("\n")) {
                if (ConfigManager.getJobsConfiguration().isBroadcastingLevelups()) {
                    Jobs.getServer().broadcastMessage(line);
                } else if (player != null) {
                    player.sendMessage(line);
                }
            }
        }
        prog.setTitle(levelTitle);
        jPlayer.reloadHonorific();
        Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
    }
    
    /**
     * Perform reload
     */
    public void reload() {
        synchronized (players) {
            for (JobsPlayer jPlayer : players.values()) {
                for (JobProgression progression : jPlayer.getJobProgression()) {
                    String jobName = progression.getJob().getName();
                    Job job = Jobs.getJob(jobName);
                    if (job != null) {
                        progression.setJob(job);
                    }
                }
                if (jPlayer.isOnline()) {
                    jPlayer.reloadHonorific();
                    Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
                }
            }
        }
    }
}
